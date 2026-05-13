package com.fastmask.data.demo

import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import java.time.Duration
import java.time.Instant

/**
 * Static seed data used by the in-memory demo repository.
 *
 * The list is anchored to a fixed "today" reference [DEMO_NOW] so screenshots and demo
 * walkthroughs render identically regardless of wall-clock time. Each entry covers a
 * different state / domain / recency bucket so the list, detail, filter, and search
 * surfaces all have realistic content to display.
 */
internal val DEMO_NOW: Instant = Instant.parse("2026-05-13T10:00:00Z")

private fun daysAgo(days: Long): Instant = DEMO_NOW.minus(Duration.ofDays(days))
private fun monthsAgo(months: Long): Instant = DEMO_NOW.minus(Duration.ofDays(months * 30))
private fun hoursAgo(hours: Long): Instant = DEMO_NOW.minus(Duration.ofHours(hours))

val INITIAL_DEMO_MASKS: List<MaskedEmail> = listOf(
    MaskedEmail(
        id = "demo-001",
        email = "quiet.harbor942@fastmask.com",
        state = EmailState.ENABLED,
        forDomain = "amazon.com",
        description = "Amazon",
        createdBy = "demo",
        url = "https://amazon.com",
        emailPrefix = "quiet.harbor",
        createdAt = monthsAgo(14),
        lastMessageAt = hoursAgo(3)
    ),
    MaskedEmail(
        id = "demo-002",
        email = "blue.morning315@fastmask.com",
        state = EmailState.ENABLED,
        forDomain = "stripe.com",
        description = "Stripe receipts",
        createdBy = "demo",
        url = "https://stripe.com",
        emailPrefix = "blue.morning",
        createdAt = monthsAgo(9),
        lastMessageAt = daysAgo(1)
    ),
    MaskedEmail(
        id = "demo-003",
        email = "calm.river078@fastmask.com",
        state = EmailState.ENABLED,
        forDomain = "notion.so",
        description = "Notion",
        createdBy = "demo",
        url = "https://notion.so",
        emailPrefix = "calm.river",
        createdAt = monthsAgo(7),
        lastMessageAt = daysAgo(3)
    ),
    MaskedEmail(
        id = "demo-004",
        email = "gentle.bridge501@fastmask.com",
        state = EmailState.ENABLED,
        forDomain = "vercel.com",
        description = "Vercel deployments",
        createdBy = "demo",
        url = "https://vercel.com",
        emailPrefix = "gentle.bridge",
        createdAt = monthsAgo(11),
        lastMessageAt = daysAgo(7)
    ),
    MaskedEmail(
        id = "demo-005",
        email = "warm.silk862@fastmask.com",
        state = EmailState.ENABLED,
        forDomain = "dropbox.com",
        description = "Dropbox trial",
        createdBy = "demo",
        url = "https://dropbox.com",
        emailPrefix = "warm.silk",
        createdAt = monthsAgo(3),
        lastMessageAt = daysAgo(14)
    ),
    MaskedEmail(
        id = "demo-006",
        email = "bright.echo284@fastmask.com",
        state = EmailState.ENABLED,
        forDomain = "substack.com",
        description = "Newsletter signup",
        createdBy = "demo",
        url = "https://substack.com",
        emailPrefix = "bright.echo",
        createdAt = monthsAgo(5),
        lastMessageAt = daysAgo(5)
    ),
    MaskedEmail(
        id = "demo-007",
        email = "clever.path619@fastmask.com",
        state = EmailState.ENABLED,
        forDomain = "hetzner.com",
        description = "Hetzner",
        createdBy = "demo",
        url = "https://hetzner.com",
        emailPrefix = "clever.path",
        createdAt = monthsAgo(18),
        lastMessageAt = monthsAgo(1)
    ),
    MaskedEmail(
        id = "demo-008",
        email = "swift.cloud447@fastmask.com",
        state = EmailState.DISABLED,
        forDomain = "groupon.com",
        description = "Groupon",
        createdBy = "demo",
        url = "https://groupon.com",
        emailPrefix = "swift.cloud",
        createdAt = monthsAgo(28),
        lastMessageAt = monthsAgo(4)
    ),
    MaskedEmail(
        id = "demo-009",
        email = "mellow.frost792@fastmask.com",
        state = EmailState.DELETED,
        forDomain = "airbnb.com",
        description = "Old Airbnb account",
        createdBy = "demo",
        url = "https://airbnb.com",
        emailPrefix = "mellow.frost",
        createdAt = monthsAgo(36),
        lastMessageAt = monthsAgo(6)
    ),
    MaskedEmail(
        id = "demo-010",
        email = "sharp.flame138@fastmask.com",
        state = EmailState.ENABLED,
        forDomain = null,
        description = "Quick test",
        createdBy = "demo",
        url = null,
        emailPrefix = "sharp.flame",
        createdAt = hoursAgo(2),
        lastMessageAt = null
    )
)
