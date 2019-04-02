// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

@file:Suppress("MemberVisibilityCanBePrivate")

package com.android.tools.probes

import com.google.monitoring.runtime.instrumentation.AllocationRecorder
import java.io.PrintWriter

/**
 * Utility class for collecting and reporting performance statistics, usually for a set of methods or classes.
 * Use by calling [enter] and [exit] before and after each traced method is called.
 */
open class PerformanceStats<K> {

    companion object {
        // Tracking time and tracking allocations require very similar code, but
        // an observation is that we never really want to track both simultaneously.
        // (Tracking allocations adds too much overhead for timing to be meaningful.)
        // Thus we switch between the two based on whether the allocation instrumentation
        // agent has been enabled.
        val trackingAllocations: Boolean
        var totalAllocations: ThreadLocal<MutableLong> = ThreadLocal.withInitial { MutableLong() }
        init {
            trackingAllocations = try {
                AllocationRecorder.addSampler { _, _, _, bytes -> totalAllocations.get().value += bytes }
                true
            } catch (e: LinkageError) {
                false
            }
        }
    }

    // Utility class to reduce allocations due to autoboxing.
    data class MutableLong(var value: Long = 0)

    // Each key has one associated entry per thread.
    class Entry<K>(val key: K) {
        var depth: Int = 0
        var startMarker: Long = 0
        var resumeMarker: Long = 0
        var total: Long = 0
        var self: Long = 0
        var callCount: Long = 0
    }

    class ThreadState<K> {
        val entries = mutableMapOf<K, Entry<K>>()
        var currEntry: Entry<K>? = null
    }

    // State local to running thread; protected by monitor because accessed by stat dumping thread.
    val localState: ThreadLocal<ThreadState<K>> = ThreadLocal.withInitial {
        val state = ThreadState<K>()
        synchronized(allState) { allState.add(state) }
        state
    }

    // List of state for all threads; protected by monitor.
    val allState = mutableListOf<ThreadState<K>>()

    fun clear() {
        synchronized(allState) {
            for (state in allState) {
                synchronized(state) {
                    with(state) {
                        entries.clear()
                        currEntry = null
                    }
                }
            }
        }
    }

    fun ThreadState<K>.get(key: K): Entry<K> {
        // As an optimization we try to reuse the last entry. This is
        // especially useful for recursive functions, for example.
        val cached = currEntry
        if (cached != null && key == cached.key)
            return cached
        return entries.getOrPut(key) { Entry(key) }
    }

    // Note: returns the parent entry to be stored on the stack.
    fun enter(key: K): Entry<K>? {
        val state = localState.get()
        synchronized(state) { // Effectively zero contention.
            with(state) {
                val parent = currEntry
                val entry = get(key)

                entry.callCount++

                if (key != parent?.key) {
                    currEntry = entry

                    val current = measure()

                    entry.resumeMarker = current
                    if (entry.depth++ == 0) {
                        entry.startMarker = current
                    }

                    if (parent != null) {
                        parent.self += current - parent.resumeMarker
                    }
                }

                return parent
            }
        }
    }

    // Note: takes the parent entry returned from [enter].
    fun exit(key: K, parent: Entry<K>?) {
        val state = localState.get()
        synchronized(state) { // Effectively zero contention.
            with(state) {
                val entry = get(key)

                if (key != parent?.key) {
                    currEntry = parent

                    val current = measure()

                    entry.self += current - entry.resumeMarker
                    if (--entry.depth == 0) {
                        entry.total += current - entry.startMarker
                    }

                    if (parent != null) {
                        parent.resumeMarker = current
                    }
                }
            }
        }
    }

    fun measure(): Long = when {
        trackingAllocations -> totalAllocations.get().value
        else -> System.nanoTime()
    }

    fun dumpStats(w: PrintWriter) {
        w.println()

        val tallies = mutableMapOf<K, Entry<K>>()
        val allState = synchronized(allState) { ArrayList(allState) }
        for (threadState in allState) {
            synchronized(threadState) {
                for ((key, entry) in threadState.entries) {
                    val tally = tallies.getOrPut(key) { Entry(key) }
                    tally.total += entry.total
                    tally.self += entry.self
                    tally.callCount += entry.callCount
                    if (entry.depth > 0) {
                        w.println("WARNING: '${prettyPrintKey(key)}' still on the stack")
                    }
                }
            }
        }

        w.println("Lint detector performance stats:")

        val spacing = 2 + (tallies.keys.map { prettyPrintKey(it).length }.max() ?: 0)
        val format = "%${spacing}s  %13s  %13s  %13s"
        w.println(String.format(format, "", "total", "self", "calls"))

        tallies.values
            .sortedByDescending { it.total }
            .forEach {
                w.println(String.format(
                    format,
                    prettyPrintKey(it.key),
                    prettyPrintValue(it.total),
                    prettyPrintValue(it.self),
                    it.callCount
                ))
            }


        w.println(String.format(format, "-", "-", "-", "-"))

        val totalTime = tallies.values.map { it.self }.sum()
        val totalCalls = tallies.values.map { it.callCount }.sum()
        w.println(String.format(
            format,
            "total",
            prettyPrintValue(totalTime),
            prettyPrintValue(totalTime),
            totalCalls
        ))

        w.println()
    }

    fun dumpStats() {
        val writer = PrintWriter(System.out)
        dumpStats(writer)
        writer.flush()
    }

    open fun prettyPrintKey(key: K): String = key.toString()

    fun prettyPrintValue(value: Long): String {
        val unit = if (trackingAllocations) "MB" else "ms"
        return "${value / 1_000_000} $unit"
    }
}

// Convenience subclass for when the keys are method names.
class MethodStats : PerformanceStats<String>()

// Convenience subclass for when the keys are class objects.
class ClassStats : PerformanceStats<Class<*>>() {
    override fun prettyPrintKey(key: Class<*>) = key.name.substringAfterLast('.')
}
