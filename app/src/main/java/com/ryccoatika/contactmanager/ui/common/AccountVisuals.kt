package com.ryccoatika.contactmanager.ui.common

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.AccountClassifier
import com.ryccoatika.contactmanager.domain.AccountClassifier.AccountKind
import com.ryccoatika.contactmanager.domain.sim.SimRouting
import kotlin.math.absoluteValue

object AccountVisuals {
    // Muted premium tones so provenance dots read as quiet metadata, not decoration.
    private val palette = listOf(
        Color(0xFF3B7DDD),
        Color(0xFF1E9E6A),
        Color(0xFFA9782F),
        Color(0xFF5D7680),
        Color(0xFF7C4A72),
        Color(0xFF2C7A70),
        Color(0xFFB4622F),
        Color(0xFF4C5A78),
    )

    fun color(accountType: String?, accountName: String?): Color =
        palette[("$accountType/$accountName".hashCode().absoluteValue) % palette.size]

    // Takes a Context so brand/provenance labels come from resources and this stays
    // callable from non-composable lambdas (buildString/joinToString) too.
    fun label(context: Context, accountType: String?, accountName: String?): String =
        when (AccountClassifier.kindOf(accountType)) {
            AccountKind.DEVICE -> {
                context.getString(R.string.account_device)
            }

            AccountKind.PHONE -> {
                context.getString(R.string.account_phone)
            }

            AccountKind.GOOGLE -> {
                accountName ?: context.getString(R.string.account_google)
            }

            AccountKind.SAMSUNG -> {
                context.getString(R.string.account_samsung)
            }

            AccountKind.WHATSAPP -> {
                context.getString(R.string.account_whatsapp)
            }

            AccountKind.TELEGRAM -> {
                context.getString(R.string.account_telegram)
            }

            AccountKind.SIM -> {
                SimRouting
                    .nativeSimSlot(accountType)
                    ?.let { context.getString(R.string.account_sim_numbered, it + 1) }
                    ?: context.getString(R.string.account_sim)
            }

            AccountKind.OTHER -> {
                accountName
                    ?: accountType?.substringAfterLast('.')
                    ?: context.getString(R.string.account_device)
            }
        }
}
