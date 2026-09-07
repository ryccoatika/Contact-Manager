package com.ryccoatika.contactmanager.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Unwrap the Compose ContextWrapper chain to the hosting Activity. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
