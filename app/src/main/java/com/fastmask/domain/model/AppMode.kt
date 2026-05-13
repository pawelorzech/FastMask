package com.fastmask.domain.model

/**
 * Runtime application mode.
 *
 * - [REAL] — production behavior. Repositories hit Fastmail's JMAP API using the user's token.
 * - [DEMO] — interactive demo with in-memory mock data. Used for onboarding, Play Store reviewers,
 *   and screenshot generation. No network calls, no persistence beyond the process.
 */
enum class AppMode {
    REAL,
    DEMO
}
