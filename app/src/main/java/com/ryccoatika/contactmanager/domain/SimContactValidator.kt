package com.ryccoatika.contactmanager.domain

import com.ryccoatika.contactmanager.domain.model.SimCapabilities

sealed interface SimValidation {
    data object Ok : SimValidation

    data class Error(
        val error: SimError,
    ) : SimValidation
}

/** Typed validation failure; resolved to user-facing text at the UI/data edge. */
sealed interface SimError {
    data object BlankName : SimError

    data class NameTooLong(
        val max: Int,
    ) : SimError

    data object BlankNumber : SimError

    data object InvalidNumber : SimError
}

/**
 * Pure validation of a name + number pair against what a SIM (EF_ADN record)
 * can store. Truncation is never applied silently: over-long names fail.
 */
object SimContactValidator {
    /** Digits with optional leading +, plus GSM dialing chars * and #. */
    private val NUMBER_REGEX = Regex("^[+]?[0-9*#]{1,20}$")

    fun validate(name: String, number: String, caps: SimCapabilities): SimValidation = when {
        name.isBlank() -> {
            SimValidation.Error(SimError.BlankName)
        }

        name.length > caps.maxNameLength -> {
            SimValidation.Error(SimError.NameTooLong(caps.maxNameLength))
        }

        number.isBlank() -> {
            SimValidation.Error(SimError.BlankNumber)
        }

        !NUMBER_REGEX.matches(number) -> {
            SimValidation.Error(SimError.InvalidNumber)
        }

        else -> {
            SimValidation.Ok
        }
    }
}
