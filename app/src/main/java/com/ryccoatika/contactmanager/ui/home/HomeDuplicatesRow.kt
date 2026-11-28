package com.ryccoatika.contactmanager.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import com.ryccoatika.contactmanager.ui.theme.TabularNums

/** "N possible duplicates · Review" — the whole row opens the Duplicates screen. */
@Composable
internal fun DuplicatesRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(R.plurals.home_duplicates_row, count, count),
                style = TabularNums.merge(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // Visual affordance only: the row itself is the click target.
            Text(
                stringResource(R.string.home_duplicates_review),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(name = "Duplicates row · light")
@Preview(name = "Duplicates row · dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DuplicatesRowPreview() {
    ContactManagerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DuplicatesRow(count = 3, onClick = {})
        }
    }
}
