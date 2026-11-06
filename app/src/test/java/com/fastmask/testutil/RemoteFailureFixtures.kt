package com.fastmask.testutil

import com.fastmask.data.api.asDomainFailure
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * What the data layer hands the rest of the app for an HTTP error status: a real
 * Retrofit `HttpException` pushed through the same translation `JmapApi` applies,
 * so tests stay faithful to what repositories actually return.
 */
fun failureForStatus(code: Int): Throwable =
    HttpException(Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType())))
        .asDomainFailure()
