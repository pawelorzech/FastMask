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
 * Never fall back to `primaryAccounts.values.firstOrNull()`: that guesses an
 * account from another scope, lets a Mail-only token "log in", and delays the
 * diagnosis until a masked-email call fails with an opaque server error.
 */
object MaskedEmailScope {

    /**
     * The account id to use, or null when the session proves the token lacks
     * the Masked Email scope.
     */
    fun accountId(session: JmapSession): String? {
        val primaryAccountId = session.primaryAccounts[FastmailEndpoints.MASKED_EMAIL_CAPABILITY]
        if (!primaryAccountId.isNullOrBlank()) {
            return primaryAccountId
        }

        // Without a primary account, exactly one capable account is still
        // unambiguous. Picking the first of several would depend on JSON map
        // order and could write to the wrong Fastmail account.
        return session.accounts.entries.singleOrNull { (_, account) ->
            account.accountCapabilities.containsKey(FastmailEndpoints.MASKED_EMAIL_CAPABILITY)
        }?.key
    }
}
