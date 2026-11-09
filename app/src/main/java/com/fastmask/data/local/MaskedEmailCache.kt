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
 * This is the user's full set of masked addresses on disk, so it is encrypted
 * with a Keystore-backed key and dropped on sign-out.
 */
@Singleton
class MaskedEmailCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    /**
     * Staging copy, in its own directory but under the SAME file name.
     *
     * The name is not cosmetic: `EncryptedFile` passes `File.getName()` to Tink
     * as the associated data of the AEAD stream, so a file encrypted as
     * `masked_emails_cache.bin.tmp` and then renamed can never be decrypted
     * again — the AAD no longer matches and every read fails the tag check.
     * Staging in a sibling directory keeps the name identical, so the rename is
     * transparent.
     */
    private val stagingFile: File
        get() = File(File(context.filesDir, STAGING_DIR).apply { mkdirs() }, FILE_NAME)

    // Two writers interleaving on one EncryptedFile produce a file whose GCM tag
    // will not verify.
    private val lock = Any()

    // Without it a slow fetch re-encrypts the account's masks onto disk after
    // log-out, which `docs/privacy.md` promises removes them.
    private val guard = GenerationGuard()

    private data class MemoryEntry(
        val owner: String,
        val fileLength: Long,
        val lastModified: Long,
        val masks: CachedMasks,
    )

    private var memoryCache: MemoryEntry? = null

    private val masterKey: MasterKey by lazy { appMasterKey(context) }

    private fun encryptedFile(target: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            target,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }

    suspend fun refresh(
        owner: String,
        now: Instant = Instant.now(),
        fetch: suspend () -> Result<List<MaskedEmail>>,
    ): Result<List<MaskedEmail>> {
        val generation = guard.current()
        return fetch().onSuccess { write(it, owner, now, generation) }
    }

    private fun write(
        masks: List<MaskedEmail>,
        owner: String,
        now: Instant,
        generation: Long,
    ) {
        synchronized(lock) {
            if (!guard.isCurrent(generation)) return
            runCatching {
                val payload = MaskedEmailSnapshotCodec.payload(masks, owner, now)
                // EncryptedFile refuses to overwrite in place, hence temp file + rename.
                val staging = stagingFile
                staging.delete()
                encryptedFile(staging).openFileOutput().use { out ->
                    out.write(MaskedEmailSnapshotCodec.encode(payload).toByteArray())
                }
                file.delete()
                if (!staging.renameTo(file)) {
                    staging.delete()
                    memoryCache = null
                } else {
                    val target = file
                    memoryCache = MemoryEntry(
                        owner = owner,
                        fileLength = target.length(),
                        lastModified = target.lastModified(),
                        masks = MaskedEmailSnapshotCodec.decoded(payload).masks,
                    )
                }
            }
        }
    }

    /**
     * A snapshot written for a different owner, or by an older build that wrote
     * none, is treated as absent. The file name is fixed and the snapshot carries
     * no addresses of its own to give a mismatch away, so this check is what keeps
     * account A's masks from being shown to account B if a sign-out cleanup failed.
     */
    fun read(owner: String): CachedMasks? = synchronized(lock) {
        runCatching {
            val target = file
            if (!target.exists()) {
                memoryCache = null
                return@synchronized null
            }
            val currentLength = target.length()
            val currentModified = target.lastModified()
            val cached = memoryCache
            if (
                cached != null &&
                cached.owner == owner &&
                cached.fileLength == currentLength &&
                cached.lastModified == currentModified
            ) {
                return@synchronized cached.masks
            }
            val bytes = encryptedFile(target).openFileInput().use { it.readBytes() }
            val snapshot = MaskedEmailSnapshotCodec.decode(String(bytes))
            if (snapshot.owner != owner) {
                return@synchronized null
            }
            val result = snapshot.masks
            memoryCache = MemoryEntry(
                owner = owner,
                fileLength = currentLength,
                lastModified = currentModified,
                masks = result,
            )
            result
        }.getOrNull()
    }

    fun clear() {
        synchronized(lock) {
            guard.advance()
            memoryCache = null
            runCatching {
                file.delete()
                stagingFile.delete()
            }
        }
    }

    private companion object {
        const val FILE_NAME = "masked_emails_cache.bin"
        const val STAGING_DIR = "cache_staging"
    }
}

internal class DecodedSnapshot(val owner: String?, val masks: CachedMasks)

/**
 * The on-disk layout is frozen: files written by earlier builds must read back
 * unchanged, so a field is only ever added as optional and never renamed.
 */
internal object MaskedEmailSnapshotCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun payload(masks: List<MaskedEmail>, owner: String?, now: Instant): CachedSnapshot =
        CachedSnapshot(
            cachedAtEpochMs = now.toEpochMilli(),
            owner = owner,
            masks = masks.map { it.toCached() },
        )

    fun encode(payload: CachedSnapshot): String =
        json.encodeToString(CachedSnapshot.serializer(), payload)

    fun decode(text: String): DecodedSnapshot =
        decoded(json.decodeFromString(CachedSnapshot.serializer(), text))

    fun decoded(payload: CachedSnapshot): DecodedSnapshot = DecodedSnapshot(
        owner = payload.owner,
        masks = CachedMasks(
            masks = payload.masks.map { it.toDomain() },
            cachedAt = Instant.ofEpochMilli(payload.cachedAtEpochMs),
        ),
    )
}

// `owner` is optional so a snapshot written by an older build still deserializes.
@Serializable
internal data class CachedSnapshot(
    val cachedAtEpochMs: Long,
    val owner: String? = null,
    val masks: List<CachedMask>,
)

@Serializable
internal data class CachedMask(
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
    // cache because of one row is not. (The wire DTO differs on purpose: an
    // absent state there is PENDING, Fastmail's documented default.)
    state = EmailState.entries.firstOrNull { it.name == state } ?: EmailState.DISABLED,
    forDomain = forDomain,
    description = description,
    createdBy = createdBy,
    url = url,
    emailPrefix = emailPrefix,
    createdAt = createdAtEpochMs?.let(Instant::ofEpochMilli),
    lastMessageAt = lastMessageAtEpochMs?.let(Instant::ofEpochMilli),
)
