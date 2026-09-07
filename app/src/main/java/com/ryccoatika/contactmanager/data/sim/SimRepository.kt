package com.ryccoatika.contactmanager.data.sim

import com.ryccoatika.contactmanager.domain.model.SimCapabilities
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SIM capability lookup — the slice ViewModels need. The write-side change
 * signal stays on [SimRepository]; only data-layer collaborators use it.
 */
interface SimStore {
    suspend fun capabilities(subId: Int?): SimCapabilities

    /** Re-probes and overwrites the cache (e.g. after a SIM swap). */
    suspend fun refreshCapabilities(subId: Int?): SimCapabilities
}

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
    ) : SimStore {
        /** Ticks after every successful SIM write; merged into the contacts flow. */
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override suspend fun capabilities(subId: Int?): SimCapabilities =
            cache.get(subId) ?: refreshCapabilities(subId)

        override suspend fun refreshCapabilities(subId: Int?): SimCapabilities {
            val caps = simSource.probe(subId)
            cache.set(subId, caps)
            return caps
        }

        fun notifySimChanged() {
            changes.tryEmit(Unit)
        }
    }
