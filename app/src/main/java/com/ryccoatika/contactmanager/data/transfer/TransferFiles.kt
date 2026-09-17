package com.ryccoatika.contactmanager.data.transfer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** The only place that touches ContentResolver/DocumentsContract for vCard transfer files. */
interface TransferFiles {
    fun openWrite(uri: Uri): OutputStream?

    fun openRead(uri: Uri): InputStream?

    /** Creates "<displayName>.vcf" inside the SAF tree; null on failure. */
    fun createInTree(treeUri: Uri, displayName: String): Uri?

    fun delete(uri: Uri)
}

@Singleton
class AndroidTransferFiles
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : TransferFiles {
        override fun openWrite(uri: Uri): OutputStream? = context.contentResolver.openOutputStream(uri, "wt")

        override fun openRead(uri: Uri): InputStream? = context.contentResolver.openInputStream(uri)

        override fun createInTree(treeUri: Uri, displayName: String): Uri? = runCatching {
            DocumentsContract.createDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
                "text/x-vcard",
                displayName,
            )
        }.getOrNull()

        override fun delete(uri: Uri) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }
    }
