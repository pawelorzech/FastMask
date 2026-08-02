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

    /**
     * Staging copy, in its own directory but under the SAME file name.
     *
     * The name is not cosmetic: `EncryptedFile` passes `File.getName()` to Tink
     * as the associated data of the AEAD stream, so a file encrypted as
     * `masked_emails_cache.bin.tmp` and then renamed can never be decrypted
     * again — the AAD no longer matches and every read fails the tag check.
     * (The instrumented round-trip test is what caught that; the JVM tests
     * cannot, because there is no Keystore there.) Staging in a sibling
     * directory keeps the name identical, so the rename is transparent.
     */
    private val stagingFile: File
        get() = File(File(context.filesDir, STAGING_DIR).apply { mkdirs() }, FILE_NAME)

    /**
     * Serializes [write] against [clear] and against itself.
     *
     * The race was invisible while every caller sat on the main thread; moving
     * the repository onto Dispatchers.IO in the same audit made two concurrent
     * writes reachable, and two writers interleaving on one EncryptedFile
     * produce a file whose GCM tag will not verify — i.e. a silently empty
     * offline list.
     */
    private val lock = Any()

    /**
     * Bumped by every [clear]. A writer captures it before starting its fetch
     * and hands it back to [write], which refuses a write stamped with a
     * generation that has since been superseded.
     *
     * [lock] alone was not enough: it orders the two file operations but says
     * nothing about which one the user asked for first. The sequence that got
     * through was pull-to-refresh on a slow link, then Settings → Log out
     * before the response landed. `logout()` clears the snapshot; the in-flight
     * continuation then re-encrypts the account's full mask list back onto
     * disk, where it stays until the next sign-in — the file is not readable by
     * a second account (the owner check sees to that), but `docs/privacy.md`
     * promises the snapshot is *removed* at log-out, and it was not.
     *
     * Cancellation could not close this: the write follows the network call
     * with no suspension point in between, so there is nothing to preempt.
     */
    private val generation = java.util.concurrent.atomic.AtomicLong(0)

    /** Snapshot of the current generation, for a caller about to fetch. */
    fun currentGeneration(): Long = generation.get()

    private fun encryptedFile(target: File): EncryptedFile {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
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
     *   fetch began. A [clear] since then means the session this data belongs
     *   to is over, and the write is dropped. Deliberately has no default: a
     *   default would be read from the *current* value at call time, which is
     *   exactly the stale-session case the parameter exists to catch, and it
     *   would let a new call site opt out of the guard by simply not knowing
     *   about it.
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
            if (generation != this.generation.get()) return
            runCatching {
                val payload = CachedSnapshot(
                    cachedAtEpochMs = now.toEpochMilli(),
                    owner = owner,
                    masks = masks.map { it.toCached() },
                )
                // Write to a temporary file and rename, so an interrupted write
                // (process death, full disk) leaves the previous good snapshot
                // in place. The old code deleted the target FIRST — because
                // EncryptedFile refuses to overwrite — which meant the window
                // between delete and flush had no cache at all, and a crash
                // inside it lost the snapshot outright.
                val staging = stagingFile
                staging.delete()
                encryptedFile(staging).openFileOutput().use { out ->
                    out.write(json.encodeToString(CachedSnapshot.serializer(), payload).toByteArray())
                }
                file.delete()
                if (!staging.renameTo(file)) {
                    staging.delete()
                }
            }
        }
    }

    /**
     * @param owner the account asking. A snapshot written for a different
     *   owner — or written before owners existed — is treated as absent rather
     *   than returned.
     *
     *   The snapshot carries no addresses of its own that would give the
     *   mismatch away, and the file name is fixed, so without this check the
     *   only thing separating one account's masks from another's was
     *   [clear] having succeeded on sign-out — an unchecked `File.delete()`.
     *   Any path that reaches a second account without a successful sign-out
     *   (a token whose keyset became unreadable, so the app starts logged out
     *   and the user signs in with a different token) would have shown account
     *   A's masks to account B, labelled as their own offline data.
     *
     * @return the snapshot, or null when there is none, it belongs to someone
     *   else, or it cannot be read.
     */
    fun read(owner: String?): CachedMasks? = runCatching {
        if (!file.exists()) return null
        val bytes = encryptedFile(file).openFileInput().use { it.readBytes() }
        val snapshot = json.decodeFromString(CachedSnapshot.serializer(), String(bytes))
        if (snapshot.owner == null || snapshot.owner != owner) {
            return null
        }
        CachedMasks(
            masks = snapshot.masks.map { it.toDomain() },
            cachedAt = Instant.ofEpochMilli(snapshot.cachedAtEpochMs),
        )
    }.getOrNull()

    /** Drops the snapshot. Called on sign-out and before a new sign-in. */
    fun clear() {
        synchronized(lock) {
            // Before the deletes, and inside the lock: any write already
            // waiting on the lock is invalidated by the time it gets in.
            generation.incrementAndGet()
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

/**
 * On-disk shape, deliberately separate from the domain model: instants become
 * epoch millis and the state becomes a plain name, so a future change to
 * [MaskedEmail] cannot silently break an existing cache file.
 */
/**
 * @param owner opaque per-account marker. Optional so a snapshot written by an
 *   older build still deserializes; [MaskedEmailCache.read] then treats the
 *   missing owner as "not mine", which degrades to no offline data until the
 *   next successful fetch — the behaviour from before the cache existed.
 */
@Serializable
private data class CachedSnapshot(
    val cachedAtEpochMs: Long,
    val owner: String? = null,
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
