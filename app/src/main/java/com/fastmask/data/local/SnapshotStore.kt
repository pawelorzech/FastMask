package com.fastmask.data.local

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Byte-level storage behind [MaskedEmailCache].
 *
 * A seam, not an abstraction for its own sake: the real store encrypts with a
 * Keystore-backed key, and there is no Keystore on the JVM, so without it every
 * assertion about the on-disk FORMAT — old files that predate a field, damaged
 * fields, fields written by a future version — could only run on a device. That
 * is the wrong place for the tests that decide whether an update crashes.
 *
 * Failures are the caller's problem to swallow: [read] may throw when the bytes
 * cannot be decrypted and [write] may throw when the disk is full.
 */
interface SnapshotStore {
    /** @return the stored bytes, or null when nothing is stored. */
    fun read(): ByteArray?

    /** Replaces the stored bytes. */
    fun write(bytes: ByteArray)

    /** Drops the stored bytes. */
    fun clear()
}

/**
 * The shipping store: one file in app-private storage, encrypted with a
 * Keystore-backed master key — the same protection [TokenStorage] gives the
 * API token.
 */
class EncryptedFileSnapshotStore(
    private val context: Context,
) : SnapshotStore {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    private fun encryptedFile(): EncryptedFile {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }

    override fun read(): ByteArray? {
        if (!file.exists()) return null
        return encryptedFile().openFileInput().use { it.readBytes() }
    }

    override fun write(bytes: ByteArray) {
        // EncryptedFile refuses to overwrite an existing file.
        if (file.exists()) file.delete()
        encryptedFile().openFileOutput().use { out -> out.write(bytes) }
    }

    override fun clear() {
        file.delete()
    }

    private companion object {
        const val FILE_NAME = "masked_emails_cache.bin"
    }
}
