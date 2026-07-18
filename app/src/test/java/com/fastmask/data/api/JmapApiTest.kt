package com.fastmask.data.api

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
    ) : JmapService {
        var lastMethodUrl: String? = null
        var executeCount = 0

        override suspend fun getSession(url: String, authHeader: String): JmapSession =
            session ?: error("no session configured")

        override suspend fun executeMethod(
            url: String,
            authHeader: String,
            request: JmapRequest
        ): JmapResponse {
            lastMethodUrl = url
            executeCount++
            return response ?: error("no response configured")
        }
    }

    private fun session(apiUrl: String) = JmapSession(
        username = "user@fastmail.com",
        apiUrl = apiUrl,
        primaryAccounts = mapOf("https://www.fastmail.com/dev/maskedemail" to "acc-1"),
        accounts = emptyMap(),
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
