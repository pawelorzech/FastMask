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

    @Test
    fun `server errors and unknown throwables use the caller fallback`() {
        assertEquals(fallback, UiErrors.messageRes(httpException(500), fallback))
        assertEquals(fallback, UiErrors.messageRes(httpException(429), fallback))
        assertEquals(fallback, UiErrors.messageRes(RuntimeException("boom"), fallback))
        assertEquals(fallback, UiErrors.messageRes(null, fallback))
    }
}
