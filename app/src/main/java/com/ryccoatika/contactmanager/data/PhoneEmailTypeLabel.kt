package com.ryccoatika.contactmanager.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone

/**
 * Maps a Data row's DATA2 (type int) + DATA3 (custom label) to a display label for
 * phone and email rows. Pure mapping over compile-time constants so it is JVM-testable.
 */
object PhoneEmailTypeLabel {

    fun from(mimeType: String?, data2Int: Int?, data3: String?): String? = when (mimeType) {
        Phone.CONTENT_ITEM_TYPE -> when (data2Int) {
            0 -> data3?.takeIf { it.isNotBlank() }
            1 -> "Home"
            2 -> "Mobile"
            3 -> "Work"
            7 -> "Other"
            else -> null
        }
        Email.CONTENT_ITEM_TYPE -> when (data2Int) {
            0 -> data3?.takeIf { it.isNotBlank() }
            1 -> "Home"
            2 -> "Work"
            3 -> "Other"
            4 -> "Mobile"
            else -> null
        }
        else -> null
    }
}
