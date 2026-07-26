package com.fastmask.data.local

import com.fastmask.domain.hygiene.HygieneBaseline
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Last known good snapshot of the account's masks, so the list is readable
 * without a network — plus the hygiene review's own baseline, which rides along
 * in the same encrypted file.
 *
 * This is the user's full set of masked addresses on disk, which the app
 * otherwise never keeps — so it is encrypted with a Keystore-backed key (see
 * [EncryptedFileSnapshotStore]), the same protection [TokenStorage] gives the
 * API token, and dropped on sign-out.
 *
 * Every failure is soft: a missing, unreadable or corrupt cache means "no
 * cache", never an error the user sees. It is derived data — the server
 * remains the source of truth, and the worst case is the offline list being
 * empty, exactly as it was before this existed.
 *
 * The class takes its storage rather than a `Context` so the file FORMAT can be
 * tested off-device (see `MaskedEmailCacheFormatTest`); Hilt builds it through
 * `RepositoryModule`.
 */
class MaskedEmailCache(
    private val store: SnapshotStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Replaces the mask list. Failures are swallowed — caching is best-effort.
     *
     * Reads the existing file first to carry the hygiene baseline forward: this
     * runs on every successful fetch, and the baseline it must not touch is the
     * one thing on that file a list refresh has no business changing.
     */
    fun write(masks: List<MaskedEmail>, now: Instant = Instant.now()) {
        runCatching {
            val retainedBaseline: JsonElement? = loadSnapshot()?.hygieneBaseline
            store.write(
                encode(
                    CachedSnapshot(
                        cachedAtEpochMs = now.toEpochMilli(),
                        masks = masks.map { it.toCached() },
                        hygieneBaseline = retainedBaseline,
                    )
                )
            )
        }
    }

    /** @return the snapshot, or null when there is none or it cannot be read. */
    fun read(): CachedMasks? = runCatching {
        val snapshot = loadSnapshot() ?: return null
        CachedMasks(
            masks = snapshot.masks.map { it.toDomain() },
            cachedAt = Instant.ofEpochMilli(snapshot.cachedAtEpochMs),
        )
    }.getOrNull()

    /**
     * @return the last persisted hygiene baseline, or null when there is none
     *   (installs that predate the field, demo mode, damaged data).
     */
    fun readHygieneBaseline(): HygieneBaseline? = runCatching {
        val element: JsonElement = loadSnapshot()?.hygieneBaseline ?: return null
        // Decoded separately from the snapshot on purpose: a baseline written
        // by a future version, or damaged in place, must cost the user their
        // "new activity" category — never their offline mask list.
        val decoded = json.decodeFromJsonElement(CachedHygieneBaseline.serializer(), element)
        HygieneBaseline(
            reviewedAt = Instant.ofEpochMilli(decoded.reviewedAtEpochMs),
            lastMessageAtById = decoded.entries
                .filter { entry -> entry.id.isNotEmpty() }
                .associate { entry ->
                    entry.id to entry.lastMessageAtEpochMs?.let(Instant::ofEpochMilli)
                },
        )
    }.getOrNull()

    /**
     * Stores the hygiene baseline alongside the current mask list.
     *
     * Attaches to an existing snapshot only. Writing a mask-less snapshot just
     * to hold a baseline would make [read] answer "cached: 0 masks" to an
     * offline list that should be saying "nothing cached at all".
     */
    fun writeHygieneBaseline(baseline: HygieneBaseline) {
        runCatching {
            val existing: CachedSnapshot = loadSnapshot() ?: return
            store.write(
                encode(
                    existing.copy(
                        hygieneBaseline = json.encodeToJsonElement(
                            CachedHygieneBaseline.serializer(),
                            baseline.toCached(),
                        )
                    )
                )
            )
        }
    }

    /** Drops the snapshot, baseline included. Called on sign-out. */
    fun clear() {
        runCatching { store.clear() }
    }

    private fun loadSnapshot(): CachedSnapshot? = runCatching {
        val bytes: ByteArray = store.read() ?: return null
        json.decodeFromString(CachedSnapshot.serializer(), String(bytes))
    }.getOrNull()

    private fun encode(snapshot: CachedSnapshot): ByteArray =
        json.encodeToString(CachedSnapshot.serializer(), snapshot).toByteArray()
}

/**
 * On-disk shape, deliberately separate from the domain model: instants become
 * epoch millis and the state becomes a plain name, so a future change to
 * [MaskedEmail] cannot silently break an existing cache file.
 */
@Serializable
private data class CachedSnapshot(
    val cachedAtEpochMs: Long,
    val masks: List<CachedMask> = emptyList(),
    /**
     * Kept opaque here and decoded on demand, so no shape this field can take —
     * a future version's, a truncated one, a string where an object belongs —
     * can fail the decode of the mask list next to it.
     */
    val hygieneBaseline: JsonElement? = null,
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

/** Every field defaulted: a half-written baseline degrades, it does not throw. */
@Serializable
private data class CachedHygieneBaseline(
    val reviewedAtEpochMs: Long = 0L,
    val entries: List<CachedHygieneEntry> = emptyList(),
)

@Serializable
private data class CachedHygieneEntry(
    val id: String = "",
    /** null = the mask existed at review time and had received nothing. */
    val lastMessageAtEpochMs: Long? = null,
)

private fun HygieneBaseline.toCached() = CachedHygieneBaseline(
    reviewedAtEpochMs = reviewedAt.toEpochMilli(),
    entries = lastMessageAtById.map { (id, lastMessageAt) ->
        CachedHygieneEntry(id = id, lastMessageAtEpochMs = lastMessageAt?.toEpochMilli())
    },
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
