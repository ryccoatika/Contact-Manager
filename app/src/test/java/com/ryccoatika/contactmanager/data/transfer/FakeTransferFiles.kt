package com.ryccoatika.contactmanager.data.transfer

import android.net.FakeUri
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Test double for [TransferFiles]; keys writes/reads by [Uri.toString]. */
class FakeTransferFiles : TransferFiles {
    val writes = mutableMapOf<String, ByteArrayOutputStream>()
    val reads = mutableMapOf<String, String>()
    val deleted = mutableListOf<String>()
    val createdNames = mutableListOf<String>()
    var openWriteFailsFor: Set<String> = emptySet()
    var writeThrowsFor: Set<String> = emptySet()
    var createFailsFor: Set<String> = emptySet()
    var openWriteThrowsSecurityFor: Set<String> = emptySet()

    override fun openWrite(uri: Uri): OutputStream? {
        val key = uri.toString()
        if (key in openWriteThrowsSecurityFor) throw SecurityException("SAF grant revoked for $key")
        if (key in openWriteFailsFor) return null
        if (key in writeThrowsFor) return ThrowingOutputStream()
        val stream = ByteArrayOutputStream()
        writes[key] = stream
        return stream
    }

    override fun openRead(uri: Uri): InputStream? =
        reads[uri.toString()]?.let { ByteArrayInputStream(it.toByteArray(Charsets.UTF_8)) }

    override fun createInTree(treeUri: Uri, displayName: String): Uri? {
        createdNames += displayName
        if (displayName in createFailsFor) return null
        return FakeUri("$treeUri/$displayName")
    }

    override fun delete(uri: Uri) {
        deleted += uri.toString()
    }

    private class ThrowingOutputStream : OutputStream() {
        override fun write(b: Int): Unit = throw IOException("boom")
    }
}
