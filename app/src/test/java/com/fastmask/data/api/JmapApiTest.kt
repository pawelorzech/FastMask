package com.fastmask.data.api

import com.fastmask.domain.auth.MaskedEmailScopeMissingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [JmapApi]: session apiUrl validation (token must never be sent to a
 * non-Fastmail host) and defensive parsing of JMAP set/get responses.
 */
class JmapApiTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private class FakeJmapService(
        var session: JmapSession? = null,
        var response: JmapResponse? = null,
        var sessionFailure: Throwable? = null,
        var executeFailure: Throwable? = null,
    ) : JmapService {
        var lastMethodUrl: String? = null
        var executeCount = 0
        var lastRequest: JmapRequest? = null

        override suspend fun getSession(url: String, authHeader: String): JmapSession =
            sessionFailure?.let { throw it } ?: session ?: error("no session configured")

        override suspend fun executeMethod(
            url: String,
            authHeader: String,
            request: JmapRequest
        ): JmapResponse {
            executeFailure?.let { throw it }
            lastMethodUrl = url
            lastRequest = request
            executeCount++
            return response ?: error("no response configured")
        }
    }

    private fun session(
        apiUrl: String,
        primaryAccounts: Map<String, String> =
            mapOf(MaskedEmailScope.CAPABILITY_URI to "acc-1"),
        accounts: Map<String, JmapAccount> = emptyMap(),
    ) = JmapSession(
        username = "user@fastmail.com",
        apiUrl = apiUrl,
        primaryAccounts = primaryAccounts,
        accounts = accounts,
        capabilities = JsonObject(emptyMap()),
        state = "s1"
    )

    private fun methodResponse(name: String, payload: JsonObject): JmapResponse =
        JmapResponse(
            methodResponses = listOf(
                buildJsonArray {
                    add(JsonPrimitive(name))
                    add(payload)
                    add(JsonPrimitive("0"))
                }
            )
        )

    // --- apiUrl validation -------------------------------------------------

    @Test
    fun `session cancellation is rethrown instead of wrapped as a failure`() = runTest {
        val api = JmapApi(
            FakeJmapService(sessionFailure = CancellationException("screen closed")),
            json,
        )

        try {
            api.getSession("tok")
            throw AssertionError("CancellationException should escape")
        } catch (_: CancellationException) {
            // Expected: structured cancellation is not an ordinary API error.
        }
    }

    @Test
    fun `method cancellation is rethrown instead of mutating stale UI state`() = runTest {
        val api = JmapApi(
            FakeJmapService(
                session = session("https://api.fastmail.com/jmap/api/"),
                executeFailure = CancellationException("screen closed"),
            ),
            json,
        )

        try {
            api.getMaskedEmails("tok")
            throw AssertionError("CancellationException should escape")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    @Test
    fun `valid fastmail apiUrl is accepted and cached`() = runTest {
        val service = FakeJmapService(session = session("https://api.fastmail.com/jmap/api/"))
        val api = JmapApi(service, json)

        val result = api.getSession("tok")

        assertTrue(result.isSuccess)
        assertEquals("acc-1", api.getAccountId())
        assertEquals("https://api.fastmail.com/jmap/api/", api.getApiUrl())
    }

    @Test
    fun `attacker apiUrl is rejected and nothing is cached`() = runTest {
        val service = FakeJmapService(session = session("https://attacker.example/jmap/"))
        val api = JmapApi(service, json)

        val result = api.getSession("tok")

        assertTrue(result.isFailure)
        assertNull(api.getAccountId())
        // Fallback stays on the hardcoded Fastmail URL.
        assertEquals(JmapService.FASTMAIL_API_URL, api.getApiUrl())
    }

    @Test
    fun `http (non-https) fastmail apiUrl is rejected`() = runTest {
        val service = FakeJmapService(session = session("http://api.fastmail.com/jmap/api/"))
        val api = JmapApi(service, json)

        assertTrue(api.getSession("tok").isFailure)
    }

    @Test
    fun `host suffix trick evilfastmail com is rejected`() = runTest {
        val service = FakeJmapService(session = session("https://evilfastmail.com/jmap/"))
        val api = JmapApi(service, json)

        assertTrue(api.getSession("tok").isFailure)
    }

    @Test
    fun `subdomain of fastmail com is accepted`() = runTest {
        val service = FakeJmapService(session = session("https://beta.fastmail.com/jmap/api/"))
        val api = JmapApi(service, json)

        assertTrue(api.getSession("tok").isSuccess)
    }

    // --- get parsing -------------------------------------------------------

    @Test
    fun `getMaskedEmails parses list from server response`() = runTest {
        val payload = buildJsonObject {
            put("accountId", "acc-1")
            put("state", "s2")
            putJsonObject("ignoredExtra") { }
            put("list", buildJsonArray {
                add(buildJsonObject {
                    put("id", "m1")
                    put("email", "quiet.harbor123@fastmail.com")
                    put("state", "enabled")
                })
            })
        }
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/get", payload)
        )
        val api = JmapApi(service, json)

        val result = api.getMaskedEmails("tok")

        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        assertEquals("m1", list[0].id)
        assertEquals(MaskedEmailState.ENABLED, list[0].state)
    }

    @Test
    fun `missing state uses Fastmail documented pending default`() = runTest {
        val payload = buildJsonObject {
            put("accountId", "acc-1")
            put("state", "s2")
            put("list", buildJsonArray {
                add(buildJsonObject {
                    put("id", "m1")
                    put("email", "pending@fastmail.com")
                })
            })
        }
        val api = JmapApi(
            FakeJmapService(
                session = session("https://api.fastmail.com/jmap/api/"),
                response = methodResponse("MaskedEmail/get", payload),
            ),
            json,
        )

        assertEquals(MaskedEmailState.PENDING, api.getMaskedEmails("tok").getOrThrow().single().state)
    }

    @Test
    fun `jmap error method response surfaces as failure not crash`() = runTest {
        val payload = buildJsonObject { put("type", "unknownAccountId") }
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("error", payload)
        )
        val api = JmapApi(service, json)

        val result = api.getMaskedEmails("tok")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is JmapException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("unknownAccountId"))
    }

    // --- single-mask get (detail screen stale-while-revalidate) -------------

    private fun singleGetPayload(vararg ids: String) = buildJsonObject {
        put("accountId", "acc-1")
        put("state", "s2")
        put("list", buildJsonArray {
            ids.forEach { id ->
                add(buildJsonObject {
                    put("id", id)
                    put("email", "$id@fastmail.com")
                    put("state", "enabled")
                })
            }
        })
    }

    @Test
    fun `getMaskedEmail restricts the fetch with the ids argument`() = runTest {
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/get", singleGetPayload("m1"))
        )
        val api = JmapApi(service, json)

        val result = api.getMaskedEmail("tok", "m1")

        assertTrue(result.isSuccess)
        assertEquals("m1", result.getOrThrow()?.id)
        // The whole point of this endpoint: the server only returns ONE mask.
        val invocation = service.lastRequest!!.methodCalls.single()
        val arguments = invocation[1] as JsonObject
        val ids = arguments["ids"] as JsonArray
        assertEquals(listOf(JsonPrimitive("m1")), ids)
    }

    @Test
    fun `getMaskedEmail maps an unknown id to success null`() = runTest {
        // Server answers a well-formed get whose list simply does not contain
        // the requested mask (deleted elsewhere / stale deep link).
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/get", singleGetPayload())
        )
        val api = JmapApi(service, json)

        val result = api.getMaskedEmail("tok", "gone")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    // --- set parsing: create ----------------------------------------------

    @Test
    fun `create success returns dto from created map`() = runTest {
        val payload = buildJsonObject {
            put("accountId", "acc-1")
            putJsonObject("created") {
                putJsonObject("new1") {
                    put("id", "m9")
                    put("email", "calm.bridge500@fastmail.com")
                }
            }
        }
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/set", payload)
        )
        val api = JmapApi(service, json)

        val result = api.createMaskedEmail("tok", MaskedEmailCreate())

        assertEquals("m9", result.getOrThrow().id)
        assertEquals(
            "a partial SetResponse must retain the state sent in the request",
            MaskedEmailState.ENABLED,
            result.getOrThrow().state,
        )
    }

    @Test
    fun `create sends empty description instead of repurposing another metadata field`() = runTest {
        val payload = buildJsonObject {
            put("accountId", "acc-1")
            putJsonObject("created") {
                putJsonObject("new1") {
                    put("id", "m9")
                    put("email", "calm.bridge500@fastmail.com")
                }
            }
        }
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/set", payload),
        )
        val api = JmapApi(service, json)

        api.createMaskedEmail("tok", MaskedEmailCreate()).getOrThrow()

        val arguments = service.lastRequest!!.methodCalls.single()[1] as JsonObject
        val create = arguments["create"] as JsonObject
        val newMask = create["new1"] as JsonObject
        assertEquals(JsonPrimitive(""), newMask["description"])
        assertFalse(newMask.containsKey("url"))
        assertFalse(newMask.containsKey("forDomain"))
    }

    @Test
    fun `create rejected via notCreated surfaces error type`() = runTest {
        val payload = buildJsonObject {
            put("accountId", "acc-1")
            putJsonObject("notCreated") {
                putJsonObject("new1") {
                    put("type", "invalidProperties")
                    put("description", "bad prefix")
                }
            }
        }
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/set", payload)
        )
        val api = JmapApi(service, json)

        val result = api.createMaskedEmail("tok", MaskedEmailCreate())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("invalidProperties"))
    }

    @Test
    fun `create rate limit is exposed as a typed retryable failure`() = runTest {
        val payload = buildJsonObject {
            put("accountId", "acc-1")
            putJsonObject("notCreated") {
                putJsonObject("new1") { put("type", "rateLimit") }
            }
        }
        val api = JmapApi(
            FakeJmapService(
                session = session("https://api.fastmail.com/jmap/api/"),
                response = methodResponse("MaskedEmail/set", payload),
            ),
            json,
        )

        val result = api.createMaskedEmail("tok", MaskedEmailCreate())

        assertTrue(result.exceptionOrNull() is JmapRateLimitException)
    }

    // --- set parsing: update ----------------------------------------------

    @Test
    fun `update not confirmed by server is a failure`() = runTest {
        // Server responds without the id under `updated` — silent no-op.
        val payload = buildJsonObject { put("accountId", "acc-1") }
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/set", payload)
        )
        val api = JmapApi(service, json)

        val result = api.updateMaskedEmail("tok", "m1", MaskedEmailUpdate(description = "x"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `update confirmed under updated map succeeds`() = runTest {
        val payload = buildJsonObject {
            put("accountId", "acc-1")
            putJsonObject("updated") { put("m1", JsonPrimitive(null as String?)) }
        }
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/set", payload)
        )
        val api = JmapApi(service, json)

        assertTrue(api.updateMaskedEmail("tok", "m1", MaskedEmailUpdate(description = "x")).isSuccess)
    }

    @Test
    fun `clearing url sends explicit json null`() = runTest {
        val payload = buildJsonObject {
            put("accountId", "acc-1")
            putJsonObject("updated") { put("m1", JsonPrimitive(null as String?)) }
        }
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/set", payload),
        )
        val api = JmapApi(service, json)

        api.updateMaskedEmail("tok", "m1", MaskedEmailUpdate(clearUrl = true)).getOrThrow()

        val arguments = service.lastRequest!!.methodCalls.single()[1] as JsonObject
        val update = arguments["update"] as JsonObject
        val patch = update["m1"] as JsonObject
        assertEquals(kotlinx.serialization.json.JsonNull, patch["url"])
    }

    // --- set parsing: destroy (regression for silent no-op delete) ---------

    @Test
    fun `delete confirmed under destroyed succeeds`() = runTest {
        val payload = buildJsonObject {
            put("accountId", "acc-1")
            put("destroyed", buildJsonArray { add(JsonPrimitive("m1")) })
        }
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/set", payload)
        )
        val api = JmapApi(service, json)

        assertTrue(api.deleteMaskedEmail("tok", "m1").isSuccess)
    }

    @Test
    fun `delete not confirmed by server is a failure`() = runTest {
        // Regression: previously a response with no `destroyed` entry was
        // treated as success (silent no-op archive).
        val payload = buildJsonObject { put("accountId", "acc-1") }
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/set", payload)
        )
        val api = JmapApi(service, json)

        val result = api.deleteMaskedEmail("tok", "m1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("not confirmed"))
    }

    @Test
    fun `delete rejected via notDestroyed surfaces error`() = runTest {
        val payload = buildJsonObject {
            put("accountId", "acc-1")
            putJsonObject("notDestroyed") {
                putJsonObject("m1") { put("type", "notFound") }
            }
        }
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = methodResponse("MaskedEmail/set", payload)
        )
        val api = JmapApi(service, json)

        val result = api.deleteMaskedEmail("tok", "m1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("notFound"))
    }

    // --- session lifecycle -------------------------------------------------

    @Test
    fun `clearSession drops cached account and url`() = runTest {
        val service = FakeJmapService(session = session("https://api.fastmail.com/jmap/api/"))
        val api = JmapApi(service, json)
        api.getSession("tok")
        assertNotNull(api.getAccountId())

        api.clearSession()

        assertNull(api.getAccountId())
        assertEquals(JmapService.FASTMAIL_API_URL, api.getApiUrl())
    }

    // --- masked email scope diagnosis (regression: silent account fallback) --

    /**
     * A token granted only Mail scope authenticates fine, so `getSession`
     * used to succeed and quietly cache the *mail* account id. Every masked
     * address call then failed with an opaque JMAP error, several screens
     * later, with nothing telling the user their token scope was wrong.
     *
     * The diagnosis belongs at login: fail here, precisely.
     */
    @Test
    fun `getSession fails when the token has no masked email scope`() = runTest {
        val service = FakeJmapService(
            session = session(
                apiUrl = "https://api.fastmail.com/jmap/api/",
                primaryAccounts = mapOf("urn:ietf:params:jmap:mail" to "acc-mail"),
            )
        )
        val api = JmapApi(service, json)

        val result = api.getSession("tok")

        assertTrue("login must not succeed on a scopeless token", result.isFailure)
        assertTrue(
            "expected a scope-specific failure, got: ${result.exceptionOrNull()}",
            result.exceptionOrNull() is MaskedEmailScopeMissingException,
        )
    }

    @Test
    fun `a scopeless session caches no account id`() = runTest {
        val service = FakeJmapService(
            session = session(
                apiUrl = "https://api.fastmail.com/jmap/api/",
                primaryAccounts = mapOf("urn:ietf:params:jmap:mail" to "acc-mail"),
            )
        )
        val api = JmapApi(service, json)

        api.getSession("tok")

        assertNull(api.getAccountId())
    }

    /**
     * The consequence that matters: no masked-email request may ever be
     * addressed to an account borrowed from another scope.
     */
    @Test
    fun `masked email calls never run against a borrowed account`() = runTest {
        val service = FakeJmapService(
            session = session(
                apiUrl = "https://api.fastmail.com/jmap/api/",
                primaryAccounts = mapOf(
                    "urn:ietf:params:jmap:mail" to "acc-mail",
                    "urn:ietf:params:jmap:contacts" to "acc-contacts",
                ),
            )
        )
        val api = JmapApi(service, json)

        val result = api.getMaskedEmails("tok")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MaskedEmailScopeMissingException)
        assertEquals("no JMAP method call should have been attempted", 0, service.executeCount)
    }

    @Test
    fun `a session that grants the scope still logs in`() = runTest {
        val service = FakeJmapService(session = session("https://api.fastmail.com/jmap/api/"))
        val api = JmapApi(service, json)

        val result = api.getSession("tok")

        assertTrue(result.isSuccess)
        assertEquals("acc-1", api.getAccountId())
    }

    @Test
    fun `empty method responses fail gracefully`() = runTest {
        val service = FakeJmapService(
            session = session("https://api.fastmail.com/jmap/api/"),
            response = JmapResponse(methodResponses = emptyList())
        )
        val api = JmapApi(service, json)

        val result = api.getMaskedEmails("tok")

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is NullPointerException)
    }
}
