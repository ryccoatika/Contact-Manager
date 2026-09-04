package com.ryccoatika.contactmanager.data.sim

import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Capability lookup with a persistent probe cache, plus the change signal
 * SIM writers emit so the contacts flow re-reads icc storage (the icc
 * provider has no ContentObserver notifications of its own).
 */
@Singleton
class SimRepository
    @Inject
    constructor(
        private val simSource: SimContactSource,
        private val cache: SimCapabilityCache,
    ) {
        /** Ticks after every successful SIM write; merged into the contacts flow. */
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        suspend fun capabilities(subId: Int?): SimCapabilities =
            cache.get(subId) ?: refreshCapabilities(subId)

        /** Re-probes and overwrites the cache (e.g. after a SIM swap). */
        suspend fun refreshCapabilities(subId: Int?): SimCapabilities {
            val caps = simSource.probe(subId)
            cache.set(subId, caps)
            return caps
        }

        fun notifySimChanged() {
            changes.tryEmit(Unit)
        }
    }
