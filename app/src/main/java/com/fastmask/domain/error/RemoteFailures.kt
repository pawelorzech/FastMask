package com.fastmask.domain.error

/** The server refused the credential (HTTP 401/403): signing in again is the only fix. */
class AuthRejectedException(cause: Throwable? = null) :
    Exception("Authentication rejected", cause)

/** Fastmail asked the client to slow down (HTTP 429 or a JMAP `rateLimit` error). */
class RateLimitedException(message: String = "Rate limited", cause: Throwable? = null) :
    Exception(message, cause)

/** Fastmail failed on its side (HTTP 5xx); repeating the request may succeed. */
class ServerErrorException(cause: Throwable? = null) :
    Exception("Server error", cause)
