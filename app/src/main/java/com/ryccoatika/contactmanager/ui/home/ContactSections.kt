package com.ryccoatika.contactmanager.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ryccoatika.contactmanager.domain.model.Contact
import kotlin.math.abs

/** Fast-scroll only earns its screen space on long lists. */
internal const val FAST_SCROLL_MIN_CONTACTS = 30

internal data class ContactSection(
    val letter: Char,
    val contacts: List<Contact>,
)

/** First-letter sections in list order: A–Z first, non-letter names under "#" at the end. */
internal fun sectionsOf(contacts: List<Contact>): List<ContactSection> {
    val grouped = contacts.groupBy { contact ->
        val first = contact.displayName
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
        if (first?.isLetter() == true) first else '#'
    }
    val letters = grouped.keys.filter { it != '#' }.sorted()
    return (letters + listOfNotNull('#'.takeIf { it in grouped }))
        .map { ContactSection(it, grouped.getValue(it)) }
}

/**
 * List index of the section for [letter], falling back to the alphabetically
 * closest populated section so dragging over empty letters still tracks.
 */
internal fun nearestSectionIndex(letter: Char, letterIndex: Map<Char, Int>): Int? {
    if (letterIndex.isEmpty()) return null
    letterIndex[letter]?.let { return it }
    if (letter == '#') return letterIndex.values.max()
    val closest = letterIndex.keys.filter { it != '#' }.minByOrNull { abs(it - letter) }
    return letterIndex[closest ?: '#']
}

@Composable
internal fun SectionHeader(letter: Char) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Text(
            letter.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
