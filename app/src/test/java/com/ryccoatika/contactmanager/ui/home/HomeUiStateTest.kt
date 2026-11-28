package com.ryccoatika.contactmanager.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    private val base = HomeUiState(duplicateCount = 3, loading = false)

    @Test
    fun `row shows with duplicates, no query, All account and no selection`() {
        assertTrue(base.showDuplicatesRow)
    }

    @Test
    fun `row hidden when there are no duplicates`() {
        assertFalse(base.copy(duplicateCount = 0).showDuplicatesRow)
    }

    @Test
    fun `row hidden while searching`() {
        assertFalse(base.copy(query = "a").showDuplicatesRow)
    }

    @Test
    fun `row hidden when an account is selected`() {
        assertFalse(base.copy(selectedAccountKey = "com.google/me@example.com").showDuplicatesRow)
    }

    @Test
    fun `row hidden in selection mode`() {
        assertFalse(base.copy(selectedContactIds = setOf(1L)).showDuplicatesRow)
    }
}
