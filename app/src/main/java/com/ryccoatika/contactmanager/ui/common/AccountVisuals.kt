package com.ryccoatika.contactmanager.ui.common

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

object AccountVisuals {
    // Muted premium tones so provenance dots read as quiet metadata, not decoration.
    private val palette = listOf(
        Color(0xFF3B7DDD), Color(0xFF1E9E6A), Color(0xFFA9782F), Color(0xFF5D7680),
        Color(0xFF7C4A72), Color(0xFF2C7A70), Color(0xFFB4622F), Color(0xFF4C5A78),
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
