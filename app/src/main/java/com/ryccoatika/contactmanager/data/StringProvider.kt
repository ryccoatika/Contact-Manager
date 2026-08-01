package com.ryccoatika.contactmanager.data

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves string resources off the composition, so ViewModels and repositories
 * can produce localized snackbar / error text without holding a Context directly.
 */
interface StringProvider {
    fun get(@StringRes id: Int, vararg args: Any): String
    fun getQuantity(@PluralsRes id: Int, count: Int, vararg args: Any): String
}

@Singleton
class AndroidStringProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : StringProvider {
    override fun get(id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

    override fun getQuantity(id: Int, count: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, count, *args)
}
