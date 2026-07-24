package com.fastmask.data.local

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last known good snapshot of the account's masks, so the list is readable
 * without a network.
 *
 * This is the user's full set of masked addresses on disk, which the app
 * otherwise never keeps — so it is encrypted with a Keystore-backed key, the
 * same protection [TokenStorage] gives the API token, and dropped on sign-out.
 *
 * Every failure is soft: a missing, unreadable or corrupt cache means "no
 * cache", never an error the user sees. It is derived data — the server
 * remains the source of truth, and the worst case is the offline list being
 * empty, exactly as it was before this existed.
 */
@Singleton
class MaskedEmailCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

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

    /** Replaces the snapshot. Failures are swallowed — caching is best-effort. */
    fun write(masks: List<MaskedEmail>, now: Instant = Instant.now()) {
        runCatching {
            // EncryptedFile refuses to overwrite an existing file.
            if (file.exists()) file.delete()
            val payload = CachedSnapshot(
                cachedAtEpochMs = now.toEpochMilli(),
                masks = masks.map { it.toCached() },
            )
            encryptedFile().openFileOutput().use { out ->
                out.write(json.encodeToString(CachedSnapshot.serializer(), payload).toByteArray())
            }
        }
    }

    /** @return the snapshot, or null when there is none or it cannot be read. */
    fun read(): CachedMasks? = runCatching {
        if (!file.exists()) return null
        val bytes = encryptedFile().openFileInput().use { it.readBytes() }
        val snapshot = json.decodeFromString(CachedSnapshot.serializer(), String(bytes))
        CachedMasks(
            masks = snapshot.masks.map { it.toDomain() },
            cachedAt = Instant.ofEpochMilli(snapshot.cachedAtEpochMs),
        )
    }.getOrNull()

    /** Drops the snapshot. Called on sign-out. */
    fun clear() {
        runCatching { file.delete() }
    }

    private companion object {
        const val FILE_NAME = "masked_emails_cache.bin"
    }
}

/**
 * On-disk shape, deliberately separate from the domain model: instants become
 * epoch millis and the state becomes a plain name, so a future change to
 * [MaskedEmail] cannot silently break an existing cache file.
 */
@Serializable
private data class CachedSnapshot(
    val cachedAtEpochMs: Long,
    val masks: List<CachedMask>,
)

@Serializable
private data class CachedMask(
    val id: String,
    val email: String,
    val state: String,
    val forDomain: String? = null,
    val description: String? = null,
    val createdBy: String? = null,
    val url: String? = null,
    val emailPrefix: String? = null,
    val createdAtEpochMs: Long? = null,
    val lastMessageAtEpochMs: Long? = null,
)

private fun MaskedEmail.toCached() = CachedMask(
    id = id,
    email = email,
    state = state.name,
    forDomain = forDomain,
    description = description,
    createdBy = createdBy,
    url = url,
    emailPrefix = emailPrefix,
    createdAtEpochMs = createdAt?.toEpochMilli(),
    lastMessageAtEpochMs = lastMessageAt?.toEpochMilli(),
)

private fun CachedMask.toDomain() = MaskedEmail(
    id = id,
    email = email,
    // An unknown state name (older or newer app version) degrades to DISABLED
    // rather than throwing: showing a mask as off is safe, losing the whole
    // cache because of one row is not.
    state = runCatching { EmailState.valueOf(state) }.getOrDefault(EmailState.DISABLED),
    forDomain = forDomain,
    description = description,
    createdBy = createdBy,
    url = url,
    emailPrefix = emailPrefix,
    createdAt = createdAtEpochMs?.let(Instant::ofEpochMilli),
    lastMessageAt = lastMessageAtEpochMs?.let(Instant::ofEpochMilli),
)
