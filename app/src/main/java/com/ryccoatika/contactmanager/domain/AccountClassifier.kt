package com.ryccoatika.contactmanager.domain

import com.ryccoatika.contactmanager.domain.model.AccountCapability

/**
 * Classifies a RawContacts account_type into what operations we allow.
 * Unknown third-party types default to READ_ONLY: writing into an app-managed
 * account is at best ignored and at worst clobbered by that app's sync.
 */
object AccountClassifier {
    private val FULL_CRUD_TYPES = setOf(
        "com.google",
        "com.osp.app.signin", // Samsung account
        "vnd.sec.contact.phone", // Samsung device-local
        "com.android.huawei.phone",
        "com.oppo.contacts.device",
        "vnd.oneplus.contact.phone",
        "com.xiaomi",
        "com.android.localphone",
        "com.android.contacts.default",
    )

    private val SIM_TYPES = setOf(
        "vnd.sec.contact.sim", // Samsung SIM
        "vnd.sec.contact.sim2",
        "com.android.contacts.sim",
        "com.android.sim",
        "USIM Account",
    )

    /** Account type prefix of icc/adn pseudo-accounts ("icc/<subscriptionId or -1>"). */
    private const val ICC_TYPE_PREFIX = "icc/"

    fun classify(accountType: String?): AccountCapability = when {
        accountType == null -> AccountCapability.FULL_CRUD
        accountType.startsWith(ICC_TYPE_PREFIX) -> AccountCapability.SIM
        accountType in SIM_TYPES -> AccountCapability.SIM
        accountType.contains("sim", ignoreCase = true) -> AccountCapability.SIM
        accountType in FULL_CRUD_TYPES -> AccountCapability.FULL_CRUD
        else -> AccountCapability.READ_ONLY
    }

    /** Brand/provenance bucket for display (labels, icons) — the single OEM table. */
    enum class AccountKind { DEVICE, PHONE, GOOGLE, SAMSUNG, WHATSAPP, TELEGRAM, SIM, OTHER }

    fun kindOf(accountType: String?): AccountKind = when {
        accountType == null -> AccountKind.DEVICE
        accountType == "vnd.sec.contact.phone" -> AccountKind.PHONE
        accountType == "com.google" -> AccountKind.GOOGLE
        accountType == "com.osp.app.signin" -> AccountKind.SAMSUNG
        accountType == "com.whatsapp" -> AccountKind.WHATSAPP
        accountType.startsWith("org.telegram") -> AccountKind.TELEGRAM
        classify(accountType) == AccountCapability.SIM -> AccountKind.SIM
        else -> AccountKind.OTHER
    }
}
