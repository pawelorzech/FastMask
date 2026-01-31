package com.fastmask.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface JmapService {

    @GET
    suspend fun getSession(
        @Url url: String = FASTMAIL_SESSION_URL,
        @Header("Authorization") authHeader: String
    ): JmapSession

    @POST
    suspend fun executeMethod(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: JmapRequest
    ): JmapResponse

    companion object {
        const val FASTMAIL_SESSION_URL = "https://api.fastmail.com/jmap/session"
        const val FASTMAIL_API_URL = "https://api.fastmail.com/jmap/api/"

        val JMAP_CAPABILITIES = listOf(
            "urn:ietf:params:jmap:core",
            "https://www.fastmail.com/dev/maskedemail"
        )
    }
}
