package com.fastmask.ui.common

import com.fastmask.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class UiErrorsTest {

    private val fallback = R.string.error_generic

    private fun httpException(code: Int): HttpException =
        HttpException(
            Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType()))
        )

    @Test
    fun `io exceptions map to network error`() {
        assertEquals(R.string.error_network, UiErrors.messageRes(IOException("x"), fallback))
        assertEquals(R.string.error_network, UiErrors.messageRes(UnknownHostException("dns"), fallback))
        assertEquals(R.string.error_network, UiErrors.messageRes(SocketTimeoutException("t"), fallback))
    }

    @Test
    fun `401 and 403 map to auth error`() {
        assertEquals(R.string.error_auth, UiErrors.messageRes(httpException(401), fallback))
        assertEquals(R.string.error_auth, UiErrors.messageRes(httpException(403), fallback))
    }

    // isRetryable decides whether the login screen preserves the pasted token.
    // It must agree with messageRes: every case answered with a "try again"
    // message is retryable, and an auth rejection never is.
    @Test
    fun `transport and server failures are retryable`() {
        assertEquals(true, UiErrors.isRetryable(IOException("x")))
        assertEquals(true, UiErrors.isRetryable(UnknownHostException("dns")))
        assertEquals(true, UiErrors.isRetryable(httpException(429)))
        assertEquals(true, UiErrors.isRetryable(httpException(500)))
        assertEquals(true, UiErrors.isRetryable(httpException(503)))
    }

    @Test
    fun `auth rejections and unknown throwables are not retryable`() {
        assertEquals(false, UiErrors.isRetryable(httpException(401)))
        assertEquals(false, UiErrors.isRetryable(httpException(403)))
        assertEquals(false, UiErrors.isRetryable(httpException(400)))
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
        assertEquals(R.string.error_rate_limit, UiErrors.messageRes(httpException(429), fallback))
    }

    @Test
    fun `5xx maps to server error`() {
        assertEquals(R.string.error_server, UiErrors.messageRes(httpException(500), fallback))
        assertEquals(R.string.error_server, UiErrors.messageRes(httpException(502), fallback))
        assertEquals(R.string.error_server, UiErrors.messageRes(httpException(503), fallback))
    }
}
