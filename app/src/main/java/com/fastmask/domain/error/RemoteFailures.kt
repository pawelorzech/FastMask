package com.fastmask.domain.error

/**
 * What can go wrong talking to Fastmail, in terms the rest of the app may
 * reason about. The data layer translates transport types (Retrofit's
 * `HttpException`, JMAP method errors) into these at its boundary, so nothing
 * above it needs to know which HTTP library is underneath. A dropped
 * connection stays a plain [java.io.IOException].
 */

/** The server refused the credential (HTTP 401/403): signing in again is the only fix. */
class AuthRejectedException(cause: Throwable? = null) :
    Exception("Authentication rejected", cause)

/** Fastmail asked the client to slow down (HTTP 429 or a JMAP `rateLimit` error). */
class RateLimitedException(message: String = "Rate limited", cause: Throwable? = null) :
    Exception(message, cause)

/** Fastmail failed on its side (HTTP 5xx); repeating the request may succeed. */
class ServerErrorException(cause: Throwable? = null) :
    Exception("Server error", cause)
