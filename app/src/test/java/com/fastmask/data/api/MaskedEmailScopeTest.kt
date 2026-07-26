package com.fastmask.data.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which account a masked-email JMAP method call may be addressed to — and,
 * above all,
 * when the answer must be "none, this token has the wrong scope".
 *
 * The defect this pins down: the session lookup used to read
 *
 *     primaryAccounts[maskedemail] ?: primaryAccounts.values.firstOrNull()
 *
 * so a token granted only Mail scope produced a perfectly successful login
 * against someone's *mail* account id. The failure then surfaced several
 * screens later as an opaque JMAP error, with nothing pointing at the token's
 * scope. The fallback has to go.
 */
class MaskedEmailScopeTest {

    private val mailCapability = "urn:ietf:params:jmap:mail"
    private val contactsCapability = "urn:ietf:params:jmap:contacts"

    private fun session(
        primaryAccounts: Map<String, String> = emptyMap(),
        accounts: Map<String, JmapAccount> = emptyMap(),
        capabilities: JsonObject = JsonObject(emptyMap()),
    ) = JmapSession(
        username = "user@fastmail.com",
        apiUrl = "https://api.fastmail.com/jmap/api/",
        primaryAccounts = primaryAccounts,
        accounts = accounts,
        capabilities = capabilities,
        state = "s1",
    )

    private fun account(vararg capabilities: String) = JmapAccount(
        name = "an account",
        accountCapabilities = buildJsonObject {
            capabilities.forEach { putJsonObject(it) { } }
        },
    )

    private fun capabilities(vararg uris: String) = buildJsonObject {
        uris.forEach { putJsonObject(it) { } }
    }

    // --- the scope is present ----------------------------------------------

    @Test
    fun `primary masked email account is the account id`() {
        val session = session(
            primaryAccounts = mapOf(
                mailCapability to "acc-mail",
                MaskedEmailScope.CAPABILITY_URI to "acc-masked",
            ),
        )

        assertEquals("acc-masked", MaskedEmailScope.accountId(session))
    }

    // --- the scope is absent: the regression ------------------------------

    @Test
    fun `a token with only mail scope is rejected instead of borrowing the mail account`() {
        val session = session(
            primaryAccounts = mapOf(
                mailCapability to "acc-mail",
                contactsCapability to "acc-contacts",
            ),
            accounts = mapOf(
                "acc-mail" to account(mailCapability),
                "acc-contacts" to account(contactsCapability),
            ),
        )

        assertNull(
            "a mail account id must never stand in for a masked-email account",
            MaskedEmailScope.accountId(session),
        )
    }

    @Test
    fun `a session with no primary accounts at all is rejected`() {
        assertNull(MaskedEmailScope.accountId(session()))
    }

    @Test
    fun `an empty masked email account id is rejected`() {
        val session = session(
            primaryAccounts = mapOf(
                MaskedEmailScope.CAPABILITY_URI to "",
                mailCapability to "acc-mail",
            ),
        )

        assertNull(MaskedEmailScope.accountId(session))
    }

    @Test
    fun `a blank masked email account id is rejected`() {
        val session = session(
            primaryAccounts = mapOf(MaskedEmailScope.CAPABILITY_URI to "   "),
        )

        assertNull(MaskedEmailScope.accountId(session))
    }

    /**
     * The key is matched exactly. A near-miss must not be read as the scope —
     * substring matching on "maskedemail" would accept a future unrelated key.
     */
    @Test
    fun `a lookalike capability key does not count as the masked email scope`() {
        val session = session(
            primaryAccounts = mapOf(
                "${MaskedEmailScope.CAPABILITY_URI}v2" to "acc-other",
            ),
        )

        assertNull(MaskedEmailScope.accountId(session))
    }

    // --- the narrow, evidence-gated fallback -------------------------------

    /**
     * The one fallback that survives: Fastmail may omit the primaryAccounts
     * entry while the account itself still advertises the capability under
     * `accounts[id].accountCapabilities`. That is positive evidence of the
     * scope for a *specific* account, so it is safe to use — and it is chosen
     * by capability, never by position, which is why the account carrying it
     * here is deliberately not the first one.
     */
    @Test
    fun `an account advertising the capability is used when the primary entry is missing`() {
        val session = session(
            primaryAccounts = mapOf(mailCapability to "acc-mail"),
            accounts = mapOf(
                "acc-mail" to account(mailCapability),
                "acc-masked" to account(mailCapability, MaskedEmailScope.CAPABILITY_URI),
            ),
        )

        assertEquals("acc-masked", MaskedEmailScope.accountId(session))
    }

    /**
     * Session-level `capabilities` describe what the *server* supports, not
     * what this token may reach or which account holds it. On their own they
     * authorize nothing — otherwise the old "first account" guess returns
     * through the back door.
     */
    @Test
    fun `server capabilities alone do not authorize picking an account`() {
        val session = session(
            primaryAccounts = mapOf(mailCapability to "acc-mail"),
            accounts = mapOf("acc-mail" to account(mailCapability)),
            capabilities = capabilities(mailCapability, MaskedEmailScope.CAPABILITY_URI),
        )

        assertNull(MaskedEmailScope.accountId(session))
    }

    @Test
    fun `an empty masked email primary entry falls through to an account that advertises the scope`() {
        val session = session(
            primaryAccounts = mapOf(MaskedEmailScope.CAPABILITY_URI to ""),
            accounts = mapOf("acc-masked" to account(MaskedEmailScope.CAPABILITY_URI)),
        )

        assertEquals("acc-masked", MaskedEmailScope.accountId(session))
    }
}
