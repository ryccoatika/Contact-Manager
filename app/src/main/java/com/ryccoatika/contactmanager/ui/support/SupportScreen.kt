package com.ryccoatika.contactmanager.ui.support

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.R
import com.ryccoatika.contactmanager.data.billing.BillingEvent
import com.ryccoatika.contactmanager.ui.analytics.TrackScreenView
import com.ryccoatika.contactmanager.ui.common.SectionCard
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme

private data class SupportTier(
    val productId: String,
    val emoji: String,
    @StringRes val nameRes: Int,
    val fallbackPrice: String,
)

private val TIERS = listOf(
    SupportTier("support_coffee", "☕", R.string.support_coffee, "$1"),
    SupportTier("support_smoothie", "🥤", R.string.support_smoothie, "$4"),
    SupportTier("support_pizza", "🍕", R.string.support_pizza, "$8"),
    SupportTier("support_fancy_meal", "🍽️", R.string.support_fancy_meal, "$10"),
)

@Composable
fun SupportScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: SupportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val prices by viewModel.prices.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val thanks = stringResource(R.string.support_thanks)
    val cancelled = stringResource(R.string.support_cancelled)
    val error = stringResource(R.string.support_error)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    BillingEvent.PurchaseSuccess -> thanks
                    BillingEvent.PurchaseCancelled -> cancelled
                    BillingEvent.Error -> error
                },
            )
        }
    }

    SupportContent(
        prices = prices,
        onPurchase = { productId -> activity?.let { viewModel.purchase(it, productId) } },
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        embedded = embedded,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportContent(
    prices: Map<String, String>,
    onPurchase: (String) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    embedded: Boolean,
) {
    TrackScreenView("support")
    Scaffold(
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.support_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.support_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            SectionCard(Modifier.fillMaxWidth()) {
                TIERS.forEachIndexed { index, tier ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                    TierRow(
                        tier = tier,
                        price = prices[tier.productId]?.takeIf { it.isNotBlank() } ?: tier.fallbackPrice,
                        onPurchase = { onPurchase(tier.productId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TierRow(tier: SupportTier, price: String, onPurchase: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Text(
                tier.emoji,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            stringResource(tier.nameRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onPurchase) {
            Text(price)
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SupportScreenPreview() {
    ContactManagerTheme {
        SupportContent(
            prices = emptyMap(),
            onPurchase = {},
            onBack = {},
            snackbarHostState = remember { SnackbarHostState() },
            embedded = false,
        )
    }
}

/** Unwrap the Compose ContextWrapper chain to the hosting Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
