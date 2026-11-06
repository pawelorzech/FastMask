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
 * with a Keystore-backed key (as [TokenStorage] does for the API token) and
 * dropped on sign-out.
 *
 * Every failure is soft: a missing, unreadable or corrupt cache means "no
 * cache", never an error the user sees. The server remains the source of truth.
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

    /**
     * Serializes [write] against [clear] and against itself: two writers
     * interleaving on one EncryptedFile produce a file whose GCM tag will not
     * verify, i.e. a silently empty offline list.
     */
    private val lock = Any()

    /**
     * Sign-out guard: a writer takes [currentGeneration] before its fetch and
     * [write] drops the write if a [clear] happened since. Without it a slow
     * fetch re-encrypts the account's masks onto disk after log-out, which
     * `docs/privacy.md` promises removes them.
     */
    private val guard = GenerationGuard()

    /** Snapshot of the current generation, for a caller about to fetch. */
    fun currentGeneration(): Long = guard.current()

    /**
     * In-memory mirror of the last written/read snapshot, keyed by owner and
     * the on-disk file's size + mtime. Avoids re-decrypting and re-parsing the
     * full JSON list on every detail screen open (`seedFromCache`) while still
     * invalidating if the underlying file is replaced or corrupted on disk.
     */
    private data class MemoryEntry(
        val owner: String,
        val fileLength: Long,
        val lastModified: Long,
        val masks: CachedMasks,
    )

    private var memoryCache: MemoryEntry? = null

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private fun encryptedFile(target: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            target,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }

    /**
     * Replaces the snapshot. Failures are swallowed — caching is best-effort.
     *
     * @param owner identifies the account this snapshot belongs to; see [read].
     * @param generation what [currentGeneration] returned when this write's
     *   fetch began; a [clear] since then drops the write. Deliberately has no
     *   default: it would be read at call time, defeating the stale-session check.
     */
    fun write(
        masks: List<MaskedEmail>,
        owner: String?,
        now: Instant = Instant.now(),
        generation: Long,
    ) {
        synchronized(lock) {
            // Inside the lock, so a clear() cannot land between the check and
            // the rename.
            if (!guard.isCurrent(generation)) return
            runCatching {
                val payload = MaskedEmailSnapshotCodec.payload(masks, owner, now)
                // Write to a temporary file and rename, so an interrupted write
                // (process death, full disk) leaves the previous good snapshot
                // in place; EncryptedFile refuses to overwrite in place.
                val staging = stagingFile
                staging.delete()
                encryptedFile(staging).openFileOutput().use { out ->
                    out.write(MaskedEmailSnapshotCodec.encode(payload).toByteArray())
                }
                file.delete()
                if (!staging.renameTo(file)) {
                    staging.delete()
                    memoryCache = null
                } else if (owner != null) {
                    val target = file
                    memoryCache = MemoryEntry(
                        owner = owner,
                        fileLength = target.length(),
                        lastModified = target.lastModified(),
                        masks = MaskedEmailSnapshotCodec.decoded(payload).masks,
                    )
                } else {
                    memoryCache = null
                }
            }
        }
    }

    /**
     * @param owner the account asking. A snapshot written for a different
     *   owner — or written before owners existed — is treated as absent rather
     *   than returned.
     *   The file name is fixed and the snapshot carries no addresses of its own
     *   to give a mismatch away, so this check is what keeps account A's masks
     *   from being shown to account B if a sign-out cleanup failed.
     *
     * @return the snapshot, or null when there is none, it belongs to someone
     *   else, or it cannot be read.
     */
    fun read(owner: String?): CachedMasks? = synchronized(lock) {
        runCatching {
            val target = file
            if (!target.exists()) {
                memoryCache = null
                return@synchronized null
            }
            if (owner == null) return@synchronized null
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
            if (snapshot.owner == null || snapshot.owner != owner) {
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

    /** Drops the snapshot. Called on sign-out and before a new sign-in. */
    fun clear() {
        synchronized(lock) {
            // Before the deletes, and inside the lock: any write already
            // waiting on the lock is invalidated by the time it gets in.
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

/** A snapshot as read back from disk: who it belongs to plus the masks. */
internal class DecodedSnapshot(val owner: String?, val masks: CachedMasks)

/**
 * The encrypted file's plaintext format, and the only place that knows it.
 *
 * The on-disk shape is deliberately separate from the domain model and from
 * the wire DTO: instants become epoch millis and the state becomes its
 * [EmailState.name], so a change to [MaskedEmail] cannot silently break a
 * cache file an existing install already has. That layout is frozen: files
 * written by earlier builds must read back unchanged, so a field is only ever
 * added as optional and never renamed (a unit test feeds a literal old file
 * through [decode]).
 *
 * Kept free of Android and Keystore so it is unit-testable on the JVM.
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

    /** The snapshot as [decode] would return it, without the text round trip. */
    fun decoded(payload: CachedSnapshot): DecodedSnapshot = DecodedSnapshot(
        owner = payload.owner,
        masks = CachedMasks(
            masks = payload.masks.map { it.toDomain() },
            cachedAt = Instant.ofEpochMilli(payload.cachedAtEpochMs),
        ),
    )
}

/**
 * @param owner opaque per-account marker. Optional so a snapshot written by an
 *   older build still deserializes; [MaskedEmailCache.read] then treats the
 *   missing owner as "not mine", which degrades to no offline data until the
 *   next successful fetch — the behaviour from before the cache existed.
 */
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
