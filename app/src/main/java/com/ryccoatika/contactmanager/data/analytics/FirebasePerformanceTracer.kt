package com.ryccoatika.contactmanager.data.analytics

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import com.ryccoatika.contactmanager.domain.analytics.PerformanceTracer
import com.ryccoatika.contactmanager.domain.analytics.TraceScope

/**
 * Records Firebase Performance custom traces. Constructed only when Firebase is
 * initialised (see AnalyticsModule). Every Firebase call is wrapped so monitoring
 * can never fail a load; the block's own exceptions are never swallowed. The trace
 * is stopped only on normal return, so a failed or cancelled load records nothing.
 */
class FirebasePerformanceTracer(
    private val performance: FirebasePerformance,
) : PerformanceTracer {
    override suspend fun <T> trace(
        name: String,
        block: suspend TraceScope.() -> T,
    ): T {
        val trace: Trace? = runCatching { performance.newTrace(name).also { it.start() } }.getOrNull()
        val scope = object : TraceScope {
            override fun metric(
                name: String,
                value: Long,
            ) {
                runCatching { trace?.putMetric(name, value) }
            }
        }
        val result = scope.block()
        runCatching { trace?.stop() }
        return result
    }
}
