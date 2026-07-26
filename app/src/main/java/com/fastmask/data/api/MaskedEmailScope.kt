package com.fastmask.data.api

/**
 * Decides which account a masked-email JMAP method call must be addressed
 * to, given a session — and refuses to guess when the session shows no Masked Email
 * scope at all.
 *
 * Pure over [JmapSession] so the diagnosis is unit-testable without a network.
 * The narrow fallback exists because Fastmail may omit the
 * `primaryAccounts[maskedemail]` entry even while a specific account still
 * advertises the capability under `accounts[id].accountCapabilities`. That is
 * positive evidence for one concrete account, so it is safe to use.
 *
 * What must never come back is the old positional fallback
 * `primaryAccounts.values.firstOrNull()`: that guesses an account from another
 * scope, lets a Mail-only token "log in", and delays the real diagnosis until
 * a masked-email call fails later with an opaque server error.
 */
object MaskedEmailScope {

    /** Capability/primary-account key Fastmail uses for Masked Email. */
    const val CAPABILITY_URI = "https://www.fastmail.com/dev/maskedemail"

    /**
     * The account id to use, or null when the session proves the token lacks
     * the Masked Email scope.
     */
    fun accountId(session: JmapSession): String? {
        val primaryAccountId = session.primaryAccounts[CAPABILITY_URI]
        if (!primaryAccountId.isNullOrBlank()) {
            return primaryAccountId
        }

        return session.accounts.entries.firstOrNull { (_, account) ->
            account.accountCapabilities.containsKey(CAPABILITY_URI)
        }?.key
    }
}
