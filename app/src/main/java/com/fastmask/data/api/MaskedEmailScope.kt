package com.fastmask.data.api

/**
 * Decides which account a masked-email JMAP method call must be addressed
 * to, given a session — and refuses to guess when the session shows no Masked Email
 * scope at all.
 *
 * Pure over [JmapSession] so the diagnosis is unit-testable without a network.
 *
 * STUB — the body below is TODAY's (buggy) behaviour, kept so the failing
 * tests point at the real defect. See the contract in `MaskedEmailScopeTest`.
 */
object MaskedEmailScope {

    /** Capability/primary-account key Fastmail uses for Masked Email. */
    const val CAPABILITY_URI = "https://www.fastmail.com/dev/maskedemail"

    /**
     * The account id to use, or null when the session proves the token lacks
     * the Masked Email scope.
     */
    fun accountId(session: JmapSession): String? =
        session.primaryAccounts[CAPABILITY_URI]
            ?: session.primaryAccounts.values.firstOrNull()
}
