package com.ryccoatika.contactmanager.data.sim

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
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
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FALLBACK
        }
    }

    private companion object {
        val FALLBACK = listOf(SimSubscription(subscriptionId = null, label = "SIM"))
    }
}
