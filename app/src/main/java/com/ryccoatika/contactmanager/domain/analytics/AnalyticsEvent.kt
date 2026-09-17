package com.ryccoatika.contactmanager.domain.analytics

/** Every analytics event. Params are non-PII only: counts, enums, booleans, lengths. */
sealed class AnalyticsEvent(
    val name: String,
    val params: Map<String, Any> = emptyMap(),
) {
    data class ContactMove(
        val count: Int,
        val targetCapability: String,
    ) : AnalyticsEvent("contact_move", mapOf("count" to count, "target_capability" to targetCapability))

    data class ContactCopy(
        val count: Int,
        val targetCapability: String,
    ) : AnalyticsEvent("contact_copy", mapOf("count" to count, "target_capability" to targetCapability))

    data class ContactDelete(
        val count: Int,
    ) : AnalyticsEvent("contact_delete", mapOf("count" to count))

    data class ContactsMerge(
        val count: Int,
    ) : AnalyticsEvent("contacts_merge", mapOf("count" to count))

    data class ContactsLink(
        val count: Int,
    ) : AnalyticsEvent("contacts_link", mapOf("count" to count))

    data class ContactCreate(
        val accountCapability: String,
    ) : AnalyticsEvent("contact_create", mapOf("account_capability" to accountCapability))

    data object ContactUpdate : AnalyticsEvent("contact_update")

    data class AccountVisibility(
        val hidden: Boolean,
    ) : AnalyticsEvent(if (hidden) "account_hide" else "account_show")

    data class AccountMoveAll(
        val count: Int,
    ) : AnalyticsEvent("account_move_all", mapOf("count" to count))

    data class AccountCopyAll(
        val count: Int,
    ) : AnalyticsEvent("account_copy_all", mapOf("count" to count))

    data class Search(
        val queryLength: Int,
    ) : AnalyticsEvent("search", mapOf("query_length" to queryLength))

    data object DuplicateDismiss : AnalyticsEvent("duplicate_dismiss")

    data object CallContact : AnalyticsEvent("call_contact")

    data object MessageContact : AnalyticsEvent("message_contact")

    data class Onboarding(
        val skipped: Boolean,
    ) : AnalyticsEvent(if (skipped) "onboarding_skip" else "onboarding_complete")

    data object SimPermissionGrant : AnalyticsEvent("sim_permission_grant")

    data class ContactsExport(
        val count: Int,
        val accountCount: Int,
        val perAccountFiles: Boolean,
    ) : AnalyticsEvent(
            "contacts_export",
            mapOf("count" to count, "account_count" to accountCount, "per_account_files" to perAccountFiles),
        )

    data class ContactsImport(
        val count: Int,
        val accountCount: Int,
        val simTarget: Boolean,
    ) : AnalyticsEvent(
            "contacts_import",
            mapOf("count" to count, "account_count" to accountCount, "sim_target" to simTarget),
        )
}
