package com.fastmask.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface JmapService {

    @GET
    suspend fun getSession(
        @Url url: String,
        @Header("Authorization") authHeader: String
    ): JmapSession

    @POST
    suspend fun executeMethod(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: JmapRequest
    ): JmapResponse
}
