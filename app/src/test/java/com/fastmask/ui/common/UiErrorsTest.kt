package com.fastmask.ui.common

import com.fastmask.R
import com.fastmask.domain.error.RateLimitedException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import com.fastmask.testutil.failureForStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class UiErrorsTest {

    private val fallback = R.string.error_generic

    @Test
    fun `io exceptions map to network error`() {
        assertEquals(R.string.error_network, UiErrors.messageRes(IOException("x"), fallback))
        assertEquals(R.string.error_network, UiErrors.messageRes(UnknownHostException("dns"), fallback))
        assertEquals(R.string.error_network, UiErrors.messageRes(SocketTimeoutException("t"), fallback))
    }

    @Test
    fun `401 and 403 map to auth error`() {
        assertEquals(R.string.error_auth, UiErrors.messageRes(failureForStatus(401), fallback))
        assertEquals(R.string.error_auth, UiErrors.messageRes(failureForStatus(403), fallback))
    }

    // isRetryable decides whether the login screen preserves the pasted token.
    // It must agree with messageRes: every case answered with a "try again"
    // message is retryable, and an auth rejection never is.
    @Test
    fun `transport and server failures are retryable`() {
        assertEquals(true, UiErrors.isRetryable(IOException("x")))
        assertEquals(true, UiErrors.isRetryable(UnknownHostException("dns")))
        assertEquals(true, UiErrors.isRetryable(failureForStatus(429)))
        assertEquals(true, UiErrors.isRetryable(failureForStatus(500)))
        assertEquals(true, UiErrors.isRetryable(failureForStatus(503)))
    }

    @Test
    fun `auth rejections and unknown throwables are not retryable`() {
        assertEquals(false, UiErrors.isRetryable(failureForStatus(401)))
        assertEquals(false, UiErrors.isRetryable(failureForStatus(403)))
        assertEquals(false, UiErrors.isRetryable(failureForStatus(400)))
        assertEquals(false, UiErrors.isRetryable(RuntimeException("boom")))
        assertEquals(false, UiErrors.isRetryable(null))
    }

    @Test
    fun `server errors and unknown throwables use the caller fallback`() {
        assertEquals(fallback, UiErrors.messageRes(RuntimeException("boom"), fallback))
        assertEquals(fallback, UiErrors.messageRes(null, fallback))
    }

    @Test
    fun `429 maps to rate limit error`() {
        assertEquals(R.string.error_rate_limit, UiErrors.messageRes(failureForStatus(429), fallback))
        assertEquals(
            R.string.error_rate_limit,
            UiErrors.messageRes(RateLimitedException("rateLimit"), fallback),
        )
        assertEquals(true, UiErrors.isRetryable(RateLimitedException("rateLimit")))
    }

    @Test
    fun `5xx maps to server error`() {
        assertEquals(R.string.error_server, UiErrors.messageRes(failureForStatus(500), fallback))
        assertEquals(R.string.error_server, UiErrors.messageRes(failureForStatus(502), fallback))
        assertEquals(R.string.error_server, UiErrors.messageRes(failureForStatus(503), fallback))
    }

    @Test
    fun `a refused token routes to reauthentication whether or not the operation can be retried`() {
        assertEquals(UiErrorAction.REAUTHENTICATE, UiErrors.of(failureForStatus(401), fallback, retryable = true).action)
        assertEquals(UiErrorAction.REAUTHENTICATE, UiErrors.of(failureForStatus(403), fallback, retryable = false).action)
    }

    @Test
    fun `a retryable operation offers retry for transient and unknown failures alike`() {
        assertEquals(UiError(R.string.error_network, UiErrorAction.RETRY), UiErrors.of(IOException("x"), fallback, retryable = true))
        assertEquals(UiError(fallback, UiErrorAction.RETRY), UiErrors.of(RuntimeException("boom"), fallback, retryable = true))
    }

    @Test
    fun `an operation that cannot be repeated offers no retry but keeps the message`() {
        assertEquals(UiError(R.string.error_network, UiErrorAction.NONE), UiErrors.of(IOException("x"), fallback, retryable = false))
        assertEquals(UiError(fallback, UiErrorAction.NONE), UiErrors.of(RuntimeException("boom"), fallback, retryable = false))
    }
}
