package com.ryccoatika.contactmanager.data

import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.SimError

/** Resolves a typed [SimError] to user-facing text via a [StringProvider]. */
fun SimError.toMessage(strings: StringProvider): String = when (this) {
    SimError.BlankName -> strings.get(R.string.sim_error_name_required)
    is SimError.NameTooLong -> strings.get(R.string.sim_error_name_too_long, max)
    SimError.BlankNumber -> strings.get(R.string.sim_error_number_required)
    SimError.InvalidNumber -> strings.get(R.string.sim_error_number_invalid)
}
