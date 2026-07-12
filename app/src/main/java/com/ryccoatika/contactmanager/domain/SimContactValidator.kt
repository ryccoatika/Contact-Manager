package com.ryccoatika.contactmanager.domain

import com.ryccoatika.contactmanager.domain.model.SimCapabilities

sealed interface SimValidation {
    data object Ok : SimValidation
    data class Error(val message: String) : SimValidation
}

/**
 * Pure validation of a name + number pair against what a SIM (EF_ADN record)
 * can store. Truncation is never applied silently: over-long names fail.
 */
object SimContactValidator {

    /** Digits with optional leading +, plus GSM dialing chars * and #. */
    private val NUMBER_REGEX = Regex("^[+]?[0-9*#]{1,20}$")

    fun validate(name: String, number: String, caps: SimCapabilities): SimValidation = when {
        name.isBlank() -> SimValidation.Error("Name is required.")
        name.length > caps.maxNameLength ->
            SimValidation.Error("Name too long for SIM (max ${caps.maxNameLength}).")
        number.isBlank() -> SimValidation.Error("Phone number is required.")
        !NUMBER_REGEX.matches(number) ->
            SimValidation.Error("Phone number may only contain digits, +, * and #.")
        else -> SimValidation.Ok
    }
}
