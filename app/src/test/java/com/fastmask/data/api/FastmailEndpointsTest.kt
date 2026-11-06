package com.fastmask.data.api

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastmailEndpointsTest {

    /**
     * The network security config is XML and cannot reference Kotlin, so its
     * hosts are a second copy of [FastmailEndpoints]. This is the only thing
     * stopping the two from drifting apart.
     */
    @Test
    fun `network security config trusts exactly the hosts in FastmailEndpoints`() {
        // Gradle runs unit tests with the module directory as the working directory.
        val xml = File("src/main/res/xml/network_security_config.xml")
        assertTrue("missing ${xml.absolutePath}", xml.exists())

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
        val domains = document.getElementsByTagName("domain-config").let { configs ->
            (0 until configs.length).flatMap { i ->
                val config = configs.item(i) as org.w3c.dom.Element
                val nodes = config.getElementsByTagName("domain")
                (0 until nodes.length).map { nodes.item(it).textContent.trim() }
            }
        }

        assertEquals(listOf(FastmailEndpoints.API_HOST), domains)
        assertTrue(FastmailEndpoints.API_URL.startsWith("https://${FastmailEndpoints.API_HOST}/"))
        assertTrue(FastmailEndpoints.SESSION_URL.startsWith("https://${FastmailEndpoints.API_HOST}/"))
    }

    @Test
    fun `only https urls on fastmail hosts are trusted`() {
        assertTrue(FastmailEndpoints.isTrustedHttpsUrl("https://api.fastmail.com/jmap/api/"))
        assertTrue(FastmailEndpoints.isTrustedHttpsUrl("https://beta.fastmail.com/jmap/api/"))
        assertFalse(FastmailEndpoints.isTrustedHttpsUrl("http://api.fastmail.com/jmap/api/"))
        assertFalse(FastmailEndpoints.isTrustedHttpsUrl("https://evilfastmail.com/jmap/"))
        assertFalse(FastmailEndpoints.isTrustedHttpsUrl("https://fastmail.com.evil.example/"))
        assertFalse(FastmailEndpoints.isTrustedHttpsUrl("not a url"))
    }
}
