package com.fastmask.data.billing

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies the cryptographic signature Google Play attaches to every purchase,
 * so a forged/replayed purchase (e.g. a hooked billing service on a rooted
 * device) cannot unlock Pro. Mirrors the reference implementation in Google's
 * Play Billing sample (`Security.java`): `SHA1withRSA` over the purchase's
 * `originalJson`, checked against the app's Base64-encoded RSA public key from
 * the Play Console (Monetization setup → Licensing).
 *
 * The public key is a PUBLIC value (it ships in the APK regardless); keeping it
 * out of source only raises the bar against trivial APK patching.
 */
object PurchaseSecurity {

    private const val KEY_FACTORY_ALGORITHM = "RSA"
    private const val SIGNATURE_ALGORITHM = "SHA1withRSA"

    @Volatile
    private var cachedPublicKey: Pair<String, PublicKey>? = null

    fun verify(base64PublicKey: String, signedData: String, signature: String): Boolean {
        if (base64PublicKey.isBlank() || signedData.isBlank() || signature.isBlank()) return false
        return try {
            val publicKey = generatePublicKey(base64PublicKey)
            val sig = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initVerify(publicKey)
                update(signedData.toByteArray(Charsets.UTF_8))
            }
            sig.verify(Base64.getDecoder().decode(signature))
        } catch (e: Exception) {
            // Fail closed: an unverifiable purchase is never genuine.
            false
        }
    }

    private fun generatePublicKey(base64PublicKey: String): PublicKey {
        cachedPublicKey?.let { (cachedRaw, key) ->
            if (cachedRaw == base64PublicKey) return key
        }
        val decoded = Base64.getDecoder().decode(base64PublicKey)
        val key = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
            .generatePublic(X509EncodedKeySpec(decoded))
        cachedPublicKey = base64PublicKey to key
        return key
    }
}
