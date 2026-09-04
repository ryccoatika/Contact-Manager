package com.ryccoatika.contactmanager.domain

import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.domain.model.RawContact

/** Human-readable field names one raw contact would lose in the move. */
data class FieldLoss(
    val rawContactId: Long,
    val lostFields: List<String>,
)

data class MovePlan(
    val sources: List<RawContact>,
    val targetType: String?,
    val targetName: String?,
    /** Empty when the target supports every field of every source. */
    val losses: List<FieldLoss>,
)

/**
 * Plans a move of raw contacts into a target account. Full-CRUD targets keep
 * every field; SIM targets down-convert to name + a single phone number, so
 * the plan reports what each raw contact would lose (shown before execution).
 */
object MovePlanner {
    fun plan(sources: List<RawContact>, targetType: String?, targetName: String?): MovePlan {
        val losses = when (AccountClassifier.classify(targetType)) {
            AccountCapability.SIM -> sources.mapNotNull { simLossOf(it) }
            else -> emptyList()
        }
        return MovePlan(
            sources = sources,
            targetType = targetType,
            targetName = targetName,
            losses = losses,
        )
    }

    private fun simLossOf(raw: RawContact): FieldLoss? {
        val lost = buildList {
            if (raw.phones.size > 1) add("additional phone numbers")
            if (raw.emails.isNotEmpty()) add("emails")
            if (!raw.organization.isNullOrBlank()) add("organization")
            if (!raw.note.isNullOrBlank()) add("note")
        }
        return if (lost.isEmpty()) null else FieldLoss(raw.rawContactId, lost)
    }
}
