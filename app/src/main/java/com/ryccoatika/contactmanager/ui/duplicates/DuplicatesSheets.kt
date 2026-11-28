package com.ryccoatika.contactmanager.ui.duplicates

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.domain.DuplicateAction
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme

/** First-use explanation for Link / Not duplicate; cancelling is not remembered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExplainActionSheet(
    pending: PendingExplain,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackScreenView("duplicates_explain")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ExplainActionContent(pending.action, onConfirm = onConfirm, onCancel = onDismiss)
    }
}

@Composable
internal fun ExplainActionContent(
    action: DuplicateAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, body, confirm) = when (action) {
        DuplicateAction.LINK -> Triple(
            R.string.duplicates_explain_link_title,
            R.string.duplicates_explain_link_body,
            R.string.duplicates_link,
        )

        DuplicateAction.DISMISS -> Triple(
            R.string.duplicates_explain_dismiss_title,
            R.string.duplicates_explain_dismiss_body,
            R.string.duplicates_not_duplicate,
        )
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.duplicates_scope),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.duplicates_explain_once),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.duplicates_cancel)) }
            Button(onClick = onConfirm) { Text(stringResource(confirm)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** What Not duplicate, Link and Merge each do, plus where duplicates are searched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DuplicatesHelpSheet(onDismiss: () -> Unit) {
    TrackScreenView("duplicates_help")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        DuplicatesHelpContent()
    }
}

@Composable
internal fun DuplicatesHelpContent(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            stringResource(R.string.duplicates_help),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.duplicates_scope),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HelpEntry(R.string.duplicates_not_duplicate, R.string.duplicates_help_dismiss_body)
        HelpEntry(R.string.duplicates_link, R.string.duplicates_help_link_body)
        HelpEntry(R.string.duplicates_merge, R.string.duplicates_help_merge_body)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HelpEntry(title: Int, body: Int) {
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        stringResource(body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Preview(name = "Explain link · light")
@Preview(name = "Explain link · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExplainLinkPreview() {
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ExplainActionContent(DuplicateAction.LINK, onConfirm = {}, onCancel = {})
        }
    }
}

@Preview(name = "Explain dismiss · light")
@Preview(name = "Explain dismiss · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExplainDismissPreview() {
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ExplainActionContent(DuplicateAction.DISMISS, onConfirm = {}, onCancel = {})
        }
    }
}

@Preview(name = "Duplicates help · light")
@Preview(name = "Duplicates help · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DuplicatesHelpPreview() {
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            DuplicatesHelpContent()
        }
    }
}
