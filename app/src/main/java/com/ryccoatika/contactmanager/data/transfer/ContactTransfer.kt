package com.ryccoatika.contactmanager.data.transfer

import android.net.Uri
import com.ryccoatika.contactmanager.domain.model.ContactAccount
import com.ryccoatika.contactmanager.domain.vcard.VCardContact

sealed interface TransferResult {
    data class Success(
        val contactCount: Int,
        val fileCount: Int,
    ) : TransferResult

    data class Failure(
        val message: String,
    ) : TransferResult
}

sealed interface ParseOutcome {
    data class Parsed(
        val contacts: List<VCardContact>,
        val skippedCards: Int,
    ) : ParseOutcome

    data class Failure(
        val message: String,
    ) : ParseOutcome
}

/** vCard export/import orchestration: maps provider data to/from [VCardContact]. */
interface ContactTransfer {
    /** Writes every raw contact belonging to [accounts] into one vCard file at [uri]. */
    suspend fun exportAccounts(accounts: List<ContactAccount>, uri: Uri): TransferResult

    /** Writes one vCard file per account inside the SAF tree at [treeUri]. */
    suspend fun exportAccountsToFolder(accounts: List<ContactAccount>, treeUri: Uri): TransferResult

    /** Writes exactly the given raw contacts into one vCard file at [uri]. */
    suspend fun exportRawContacts(rawContactIds: List<Long>, uri: Uri): TransferResult

    /** Reads and parses the vCard file at [uri]. */
    suspend fun parseFile(uri: Uri): ParseOutcome

    /** Starts a batched import of [contacts] into every account in [targets]; false while another batch runs. */
    fun startImport(contacts: List<VCardContact>, targets: List<ContactAccount>): Boolean
}
