package com.ryccoatika.contactmanager.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ryccoatika.contactmanager.domain.model.Contact
import com.ryccoatika.contactmanager.ui.common.AccountVisuals

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search ${state.contacts.size} contacts") },
                singleLine = true,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedAccountKey == null,
                        onClick = { viewModel.selectAccount(null) },
                        label = { Text("All") },
                    )
                }
                items(state.accounts, key = { it.key }) { account ->
                    FilterChip(
                        selected = state.selectedAccountKey == account.key,
                        onClick = { viewModel.selectAccount(account.key) },
                        label = { Text("${AccountVisuals.label(account.type, account.name)} · ${account.contactCount}") },
                        leadingIcon = {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(AccountVisuals.color(account.type, account.name)),
                            )
                        },
                    )
                }
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.contacts, key = { it.contactId }) { contact ->
                        ContactRow(contact)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: Contact) {
    ListItem(
        headlineContent = { Text(contact.displayName) },
        leadingContent = { ContactAvatar(contact) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                contact.rawContacts
                    .distinctBy { it.accountType to it.accountName }
                    .forEach { raw ->
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(AccountVisuals.color(raw.accountType, raw.accountName)),
                        )
                    }
            }
        },
    )
}

@Composable
private fun ContactAvatar(contact: Contact) {
    if (contact.photoThumbnailUri != null) {
        AsyncImage(
            model = contact.photoThumbnailUri,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                contact.displayName.firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
