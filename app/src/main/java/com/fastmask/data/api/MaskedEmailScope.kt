package com.fastmask.data.api

/**
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

    fun accountId(session: JmapSession): String? {
        val primaryAccountId = session.primaryAccounts[FastmailEndpoints.MASKED_EMAIL_CAPABILITY]
        if (!primaryAccountId.isNullOrBlank()) {
            return primaryAccountId
        }

        return session.accounts.entries.singleOrNull { (_, account) ->
            account.accountCapabilities.containsKey(FastmailEndpoints.MASKED_EMAIL_CAPABILITY)
        }?.key
    }
}
