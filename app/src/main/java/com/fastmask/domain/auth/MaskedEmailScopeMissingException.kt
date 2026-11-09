package com.fastmask.domain.auth

/**
 * The API token authenticated successfully but carries no Masked Email scope,
 * so no masked-address call can ever succeed with it.
 *
 * The message is deliberately free of anything derived from the token.
 */
class MaskedEmailScopeMissingException : Exception("API token has no Masked Email scope")
