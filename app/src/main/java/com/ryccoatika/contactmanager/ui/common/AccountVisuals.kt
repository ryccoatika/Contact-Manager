package com.ryccoatika.contactmanager.ui.common

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

object AccountVisuals {
    private val palette = listOf(
        Color(0xFF4285F4), Color(0xFF0F9D58), Color(0xFFF4B400), Color(0xFFDB4437),
        Color(0xFF7B1FA2), Color(0xFF00838F), Color(0xFFEF6C00), Color(0xFF5D4037),
    )

    fun color(accountType: String?, accountName: String?): Color =
        palette[("$accountType/$accountName".hashCode().absoluteValue) % palette.size]

    fun label(accountType: String?, accountName: String?): String = when {
        accountType == null -> "Device"
        accountType == "com.google" -> accountName ?: "Google"
        accountType == "com.osp.app.signin" -> "Samsung"
        accountType == "com.whatsapp" -> "WhatsApp"
        accountType.startsWith("org.telegram") -> "Telegram"
        accountType.contains("sim", ignoreCase = true) -> "SIM"
        else -> accountName ?: accountType.substringAfterLast('.')
    }
}
