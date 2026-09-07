package com.ryccoatika.contactmanager.ui.review

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.ryccoatika.contactmanager.ui.common.findActivity
import kotlinx.coroutines.launch

/**
 * Play In-App Review launcher. Invoke the returned lambda right after a
 * meaningful, *completed* action — a contact moved to another account, or a
 * duplicate merged. Google's library decides whether to actually show the card
 * and enforces its own quota, so calling this on every success is safe and will
 * not nag the user. No card appears for sideloaded/debug builds; only for apps
 * installed through Play. Failures (offline, quota reached, no Play Store) are
 * swallowed by design — a review prompt is never worth surfacing an error for.
 */
@Composable
fun rememberReviewLauncher(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { ReviewManagerFactory.create(context) }
    val activity = remember(context) { context.findActivity() }
    return remember(manager, activity, scope) {
        launch@{
            val act = activity ?: return@launch
            scope.launch {
                runCatching {
                    val info = manager.requestReview()
                    manager.launchReview(act, info)
                }
            }
        }
    }
}
