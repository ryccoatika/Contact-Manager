package com.ryccoatika.contactmanager.domain.analytics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NoOpPerformanceTracerTest {
    @Test
    fun returnsBlockValueAndAcceptsMetrics() = runBlocking {
        val result = NoOpPerformanceTracer.trace(PerfTraces.CONTACTS_LOAD) {
            metric(PerfTraces.CONTACTS, 3)
            42
        }
        assertEquals(42, result)
    }

    @Test
    fun propagatesBlockException() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                NoOpPerformanceTracer.trace<Unit>(PerfTraces.ACCOUNTS_LOAD) { error("boom") }
            }
        }
    }
}
