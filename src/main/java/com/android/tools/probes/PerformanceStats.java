package com.android.tools.probes;

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;

import java.io.PrintWriter;
import java.util.*;

/**
 * Utility class for collecting and reporting performance statistics, usually for a set of methods or classes.
 * Use by calling [enter] and [exit] before and after each traced method is called.
 */
@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
public class PerformanceStats<K> {

    // Tracking time and tracking allocations require very similar code, but
    // an observation is that we never really want to track both simultaneously.
    // (Tracking allocations adds too much overhead for timing to be meaningful.)
    // Thus we switch between the two based on whether the allocation instrumentation
    // agent has been enabled.
    private static boolean trackingAllocations;
    private static final ThreadLocal<MutableLong> totalAllocations = ThreadLocal.withInitial(MutableLong::new);
    static {
        try {
            AllocationRecorder.addSampler((count, desc, newObj, size) -> totalAllocations.get().value += size);
            trackingAllocations = true;
        } catch (LinkageError e) {
            trackingAllocations = false;
        }
    }

    // Utility class to reduce autoboxing allocations.
    private static class MutableLong {
        long value = 0;
    }

    // Holds performance counters for a single tracepoint on a single thread.
    static class Tracepoint<K> {
        K key;
        int depth;
        long startMarker;
        long resumeMarker;
        long total;
        long self;
        long callCount;

        Tracepoint(K k) { key = k; }
    }

    private static class ThreadState<K> {
        Map<K, Tracepoint<K>> tracepoints = new HashMap<>();
        Tracepoint<K> currTracepoint;
    }

    // Holds the state from all threads. Protected by monitor.
    private final List<ThreadState<K>> allState = new ArrayList<>();

    // Holds the state for the current thread. Protected by monitor because it is accessed by the stat dumping thread.
    ThreadLocal<ThreadState<K>> localState = ThreadLocal.withInitial(() -> {
        ThreadState<K> state = new ThreadState<>();
        synchronized (allState) { allState.add(state); }
        return state;
    });

    void clear() {
        synchronized (allState) {
            for (ThreadState<K> state : allState) {
                synchronized (state) {
                    state.tracepoints.clear();
                    state.currTracepoint = null;
                }
            }
        }
    }

    private Tracepoint<K> getTracepoint(ThreadState<K> state, K key) {
        // As an optimization we try to reuse the last tracepoint. This is
        // especially useful for recursive functions, for example.
        Tracepoint<K> cached = state.currTracepoint;
        if (cached != null && key.equals(cached.key))
            return cached;
        return state.tracepoints.computeIfAbsent(key, Tracepoint::new);
    }

    // Note: returns the parent tracepoint to be stored on the stack.
    Tracepoint<K> enter(K key) {
        ThreadState<K> threadState = localState.get();
        synchronized (threadState) { // Almost no contention.
            Tracepoint<K> parent = threadState.currTracepoint;
            Tracepoint<K> tracepoint = getTracepoint(threadState, key);

            tracepoint.callCount++;

            if (parent == null || !key.equals(parent.key)) {
                threadState.currTracepoint = tracepoint;

                long now = measure();

                tracepoint.resumeMarker = now;
                if (tracepoint.depth++ == 0) {
                    tracepoint.startMarker = now;
                }

                if (parent != null) {
                    parent.self += now - parent.resumeMarker;
                }
            }

            return parent;
        }
    }

    // Note: takes the parent tracepoint returned from [enter].
    void exit(K key, Tracepoint<K> parent) {
        ThreadState<K> threadState = localState.get();
        synchronized (threadState) {
            Tracepoint<K> tracepoint = getTracepoint(threadState, key);

            if (parent == null || !key.equals(parent.key)) {
                threadState.currTracepoint = parent;

                long now = measure();

                tracepoint.self += now - tracepoint.resumeMarker;
                if (--tracepoint.depth == 0) {
                    tracepoint.total += now - tracepoint.startMarker;
                }

                if (parent != null)  {
                    parent.resumeMarker = now;
                }
            }
        }
    }

    private long measure() {
        return trackingAllocations ? totalAllocations.get().value : System.nanoTime();
    }

    public void dumpStats(PrintWriter w) {
        w.println();

        Map<K, Tracepoint<K>> tallies = new HashMap<>();
        List<ThreadState<K>> state;
        synchronized (allState) { state = new ArrayList<>(allState); }
        for (ThreadState<K> threadState : state) {
            synchronized (threadState) {
                for (Map.Entry<K, Tracepoint<K>> e : threadState.tracepoints.entrySet()) {
                    K key = e.getKey();
                    Tracepoint<K> tracepoint = e.getValue();
                    Tracepoint<K> tally = tallies.computeIfAbsent(key, Tracepoint::new);
                    tally.total += tracepoint.total;
                    tally.self += tracepoint.self;
                    tally.callCount += tracepoint.callCount;
                    if (tracepoint.depth > 0) {
                        w.println("WARNING: " + prettyPrintKey(key) + " is still on the stack");
                    }
                }
            }
        }

        w.println("Lint detector performance stats:");

        int spacing = tallies.keySet().stream()
                .map(it -> prettyPrintKey(it).length())
                .max(Comparator.naturalOrder())
                .orElse(0);
        spacing += 2;
        String format = "%" + spacing + "s  %13s  %13s  %13s";
        w.println(String.format(format, "", "total", "self", "calls"));

        tallies.values().stream()
                .sorted(Comparator.comparingLong((Tracepoint<K> tracepoint) -> tracepoint.total).reversed())
                .forEach(it ->
                        w.println(String.format(
                                format,
                                prettyPrintKey(it.key),
                                prettyPrintValue(it.total),
                                prettyPrintValue(it.self),
                                it.callCount
                        ))
                );
    }

    public void dumpStats() {
        PrintWriter writer = new PrintWriter(System.out);
        dumpStats(writer);
        writer.flush();
    }

    protected String prettyPrintKey(K key) {
        return key.toString();
    }

    protected String prettyPrintValue(long value) {
        String unit = trackingAllocations ? "MB" : "ms";
        return (value / 1_000_000) + " " + unit;
    }
}

// Convenience subclass for when the keys are class objects.
class ClassStats extends PerformanceStats<Class<?>> {
    @Override
    public String prettyPrintKey(Class<?> key) {
        String name = key.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot == -1 ? name : name.substring(lastDot + 1);
    }
}
