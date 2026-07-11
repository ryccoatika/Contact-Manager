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
        "com.osp.app.signin",          // Samsung account
        "vnd.sec.contact.phone",       // Samsung device-local
        "com.android.huawei.phone",
        "com.oppo.contacts.device",
        "vnd.oneplus.contact.phone",
        "com.xiaomi",
        "com.android.localphone",
        "com.android.contacts.default",
    )

    private val SIM_TYPES = setOf(
        "vnd.sec.contact.sim",         // Samsung SIM
        "vnd.sec.contact.sim2",
        "com.android.contacts.sim",
        "com.android.sim",
        "USIM Account",
    )

    fun classify(accountType: String?): AccountCapability = when {
        accountType == null -> AccountCapability.FULL_CRUD
        accountType in SIM_TYPES -> AccountCapability.SIM
        accountType.contains("sim", ignoreCase = true) -> AccountCapability.SIM
        accountType in FULL_CRUD_TYPES -> AccountCapability.FULL_CRUD
        else -> AccountCapability.READ_ONLY
    }
}
