package com.android.tools.probes;

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;

import java.io.PrintWriter;
import java.util.*;

/**
 * Utility class for collecting and reporting performance statistics, usually for a set of methods or classes.
 * Use by calling [enter] and [exit] before and after each traced method is called.
 */
@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
class PerformanceStats {

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
    static class Tracepoint {
        String id;
        int depth;
        long startMarker;
        long resumeMarker;
        long total;
        long self;
        long callCount;

        Tracepoint(String id) { this.id = id; }
    }

    private static class ThreadState {
        Map<String, Tracepoint> tracepoints = new HashMap<>();
        Tracepoint currTracepoint;
    }

    // Holds the state from all threads. Protected by monitor.
    private final List<ThreadState> allState = new ArrayList<>();

    // Holds the state for the current thread. Protected by monitor because it is accessed by the stat dumping thread.
    ThreadLocal<ThreadState> localState = ThreadLocal.withInitial(() -> {
        ThreadState state = new ThreadState();
        synchronized (allState) { allState.add(state); }
        return state;
    });

    void clear() {
        synchronized (allState) {
            for (ThreadState state : allState) {
                synchronized (state) {
                    state.tracepoints.clear();
                    state.currTracepoint = null;
                }
            }
        }
    }

    private Tracepoint getTracepoint(ThreadState state, String id) {
        // As an optimization we try to reuse the last tracepoint. This is
        // especially useful for recursive functions, for example.
        Tracepoint cached = state.currTracepoint;
        if (cached != null && id.equals(cached.id))
            return cached;
        return state.tracepoints.computeIfAbsent(id, Tracepoint::new);
    }

    // Note: returns the parent tracepoint to be stored on the stack.
    Tracepoint enter(String id) {
        ThreadState threadState = localState.get();
        synchronized (threadState) { // Almost no contention.
            Tracepoint parent = threadState.currTracepoint;
            Tracepoint tracepoint = getTracepoint(threadState, id);

            tracepoint.callCount++;

            if (parent == null || !id.equals(parent.id)) {
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
    void exit(String id, Tracepoint parent) {
        ThreadState threadState = localState.get();
        synchronized (threadState) {
            Tracepoint tracepoint = getTracepoint(threadState, id);

            if (parent == null || !id.equals(parent.id)) {
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

    void dumpStats(PrintWriter w) {
        w.println();

        Map<String, Tracepoint> tallies = new HashMap<>();
        List<ThreadState> state;
        synchronized (allState) { state = new ArrayList<>(allState); }
        for (ThreadState threadState : state) {
            synchronized (threadState) {
                for (Map.Entry<String, Tracepoint> e : threadState.tracepoints.entrySet()) {
                    String id = e.getKey();
                    Tracepoint tracepoint = e.getValue();
                    Tracepoint tally = tallies.computeIfAbsent(id, Tracepoint::new);
                    tally.total += tracepoint.total;
                    tally.self += tracepoint.self;
                    tally.callCount += tracepoint.callCount;
                    if (tracepoint.depth > 0) {
                        w.println("WARNING: " + prettyPrintId(id) + " is still on the stack");
                    }
                }
            }
        }

        w.println("Lint detector performance stats:");

        int spacing = tallies.keySet().stream()
                .map(it -> prettyPrintId(it).length())
                .max(Comparator.naturalOrder())
                .orElse(0);
        spacing += 2;
        String format = "%" + spacing + "s  %13s  %13s  %13s";
        w.println(String.format(format, "", "total", "self", "calls"));

        tallies.values().stream()
                .sorted(Comparator.comparingLong((Tracepoint tracepoint) -> tracepoint.total).reversed())
                .forEach(it ->
                        w.println(String.format(
                                format,
                                prettyPrintId(it.id),
                                prettyPrintValue(it.total),
                                prettyPrintValue(it.self),
                                it.callCount
                        ))
                );
    }

    void dumpStats() {
        PrintWriter writer = new PrintWriter(System.out);
        dumpStats(writer);
        writer.flush();
    }

    private String prettyPrintId(String id) {
        // Assumes the IDs are class names.
        int lastDot = id.lastIndexOf('.');
        return lastDot == -1 ? id : id.substring(lastDot + 1);
    }

    private String prettyPrintValue(long value) {
        String unit = trackingAllocations ? "MB" : "ms";
        return (value / 1_000_000) + " " + unit;
    }
}
