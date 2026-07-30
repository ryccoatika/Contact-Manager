package com.ryccoatika.contactmanager.data.sim

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.ryccoatika.contactmanager.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class SimSubscription(
    /** null on the single-SIM/legacy path (plain content://icc/adn). */
    val subscriptionId: Int?,
    val label: String,
    /** Physical slot this SIM occupies; null on the legacy fallback. */
    val slotIndex: Int? = null,
)

interface SimSubscriptionsSource {
    suspend fun activeSubscriptions(): List<SimSubscription>
}

/**
 * Active SIM subscriptions via SubscriptionManager. Without READ_PHONE_STATE
 * (requested lazily, may be denied forever) or on any platform hiccup this
 * degrades to a single unlabeled SIM served by the legacy icc/adn URI.
 * Never throws.
 */
@Singleton
class DefaultSimSubscriptionsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SimSubscriptionsSource {

    override suspend fun activeSubscriptions(): List<SimSubscription> = withContext(ioDispatcher) {
        try {
            val granted = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return@withContext FALLBACK
            val manager = context.getSystemService(SubscriptionManager::class.java)
                ?: return@withContext FALLBACK
            val subscriptions = manager.activeSubscriptionInfoList.orEmpty()
            if (subscriptions.isEmpty()) return@withContext FALLBACK
            subscriptions.map { info ->
                val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                SimSubscription(
                    subscriptionId = info.subscriptionId,
                    label = "SIM ${info.simSlotIndex + 1}" + (carrier?.let { " · $it" }.orEmpty()),
                    slotIndex = info.simSlotIndex,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Swallowed silently before; keep a breadcrumb since this path
            // silently degrades dual-SIM to a single SIM.
            Log.w(TAG, "activeSubscriptions failed; using single-SIM fallback", e)
            FALLBACK
        }
    }

    private companion object {
        const val TAG = "SimSubscriptions"
        val FALLBACK = listOf(SimSubscription(subscriptionId = null, label = "SIM", slotIndex = null))
    }
}
