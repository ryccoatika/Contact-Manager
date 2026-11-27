package com.ryccoatika.contactmanager.domain.analytics

/**
 * App-facing performance-trace sink. Real impl records Firebase Performance
 * custom traces; [NoOpPerformanceTracer] is used when Firebase is absent and in tests.
 *
 * Metrics are counts only — never contact data.
 */
interface PerformanceTracer {
    /**
     * Runs [block] inside a named trace. The trace is recorded only if [block]
     * returns normally; exceptions and cancellation propagate untouched.
     */
    suspend fun <T> trace(
        name: String,
        block: suspend TraceScope.() -> T,
    ): T
}

/** Attaches numeric metrics to the running trace. Counts only — never contact data. */
interface TraceScope {
    fun metric(
        name: String,
        value: Long,
    )
}

object NoOpPerformanceTracer : PerformanceTracer {
    private val scope = object : TraceScope {
        override fun metric(
            name: String,
            value: Long,
        ) = Unit
    }

    override suspend fun <T> trace(
        name: String,
        block: suspend TraceScope.() -> T,
    ): T = scope.block()
}

/** Trace and metric names. Metrics are counts only — never contact data. */
object PerfTraces {
    const val CONTACTS_LOAD = "contacts_load"
    const val ACCOUNTS_LOAD = "accounts_load"

    const val DATA_ROWS = "data_rows"
    const val CONTACTS = "contacts"
    const val SIM_CONTACTS = "sim_contacts"
    const val ACCOUNTS = "accounts"
}
