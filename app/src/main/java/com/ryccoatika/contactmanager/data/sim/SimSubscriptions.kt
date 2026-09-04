package com.ryccoatika.contactmanager.data.sim

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.ryccoatika.contactmanager.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SimSubscription(
    /** null on the single-SIM/legacy path (plain content://icc/adn). */
    val subscriptionId: Int?,
    val label: String,
    /** Physical slot this SIM occupies; null on the legacy fallback. */
    val slotIndex: Int? = null,
    /** The SIM's own phone number (MSISDN), when the platform exposes it. */
    val number: String? = null,
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
class DefaultSimSubscriptionsSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : SimSubscriptionsSource {
        override suspend fun activeSubscriptions(): List<SimSubscription> = withContext(ioDispatcher) {
            try {
                // No SIM (physical or eSIM) in any slot → no SIM account at all.
                if (!anySimPresent()) return@withContext emptyList()
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
                        number = numberOf(manager, info),
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

        /**
         * The SIM's MSISDN if the platform will share it. Prefers the deprecated
         * [SubscriptionInfo.getNumber], then [SubscriptionManager.getPhoneNumber]
         * (API 33+, needs READ_PHONE_NUMBERS). Any denial/failure yields null.
         */
        @Suppress("DEPRECATION")
        private fun numberOf(manager: SubscriptionManager, info: SubscriptionInfo): String? = try {
            val canReadNumbers = context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) ==
                PackageManager.PERMISSION_GRANTED
            info.number?.takeIf { it.isNotBlank() }
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canReadNumbers) {
                    manager.getPhoneNumber(info.subscriptionId).takeIf { it.isNotBlank() }
                } else {
                    null
                }
        } catch (e: Exception) {
            null
        }

        /**
         * True if any slot holds a usable SIM/eSIM. Uses [TelephonyManager.getSimState]
         * (no permission), so an empty tray or an eSIM with no active profile reports
         * absent and no SIM account is shown.
         */
        private fun anySimPresent(): Boolean = try {
            val tm = context.getSystemService(TelephonyManager::class.java) ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val slots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    tm.activeModemCount
                } else {
                    @Suppress("DEPRECATION")
                    tm.phoneCount
                }
                (0 until slots).any { simPresent(tm.getSimState(it)) }
            } else {
                simPresent(tm.simState)
            }
        } catch (e: Exception) {
            true // can't tell → don't hide a possibly-real SIM
        }

        private fun simPresent(state: Int): Boolean =
            state != TelephonyManager.SIM_STATE_ABSENT && state != TelephonyManager.SIM_STATE_UNKNOWN

        private companion object {
            const val TAG = "SimSubscriptions"
            val FALLBACK = listOf(SimSubscription(subscriptionId = null, label = "SIM", slotIndex = null))
        }
    }
