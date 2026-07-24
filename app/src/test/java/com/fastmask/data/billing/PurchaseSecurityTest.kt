package com.fastmask.data.billing

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseSecurityTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKeyB64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun sign(data: String): String {
        val sig = Signature.getInstance("SHA1withRSA").apply {
            initSign(keyPair.private)
            update(data.toByteArray(Charsets.UTF_8))
        }
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    @Test
    fun `valid signature verifies`() {
        val data = """{"productId":"pro_lifetime","purchaseToken":"abc"}"""
        assertTrue(PurchaseSecurity.verify(publicKeyB64, data, sign(data)))
    }

    @Test
    fun `tampered payload fails verification`() {
        val data = """{"productId":"pro_lifetime","purchaseToken":"abc"}"""
        val signature = sign(data)
        val forged = """{"productId":"pro_lifetime","purchaseToken":"HACKED"}"""
        assertFalse(PurchaseSecurity.verify(publicKeyB64, forged, signature))
    }

    @Test
    fun `signature from a different key fails`() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val data = "payload"
        val sig = Signature.getInstance("SHA1withRSA").apply {
            initSign(other.private)
            update(data.toByteArray(Charsets.UTF_8))
        }
        val foreignSig = Base64.getEncoder().encodeToString(sig.sign())
        assertFalse(PurchaseSecurity.verify(publicKeyB64, data, foreignSig))
    }

    @Test
    fun `blank inputs fail closed`() {
        assertFalse(PurchaseSecurity.verify("", "data", "sig"))
        assertFalse(PurchaseSecurity.verify(publicKeyB64, "", "sig"))
        assertFalse(PurchaseSecurity.verify(publicKeyB64, "data", ""))
    }

    @Test
    fun `malformed base64 key fails closed rather than throwing`() {
        assertFalse(PurchaseSecurity.verify("not-a-real-key", "data", "c2ln"))
    }
}
