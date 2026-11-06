package com.fastmask.domain.auth

/**
 * The API token authenticated successfully but carries no Masked Email scope,
 * so no masked-address call can ever succeed with it.
 *
 * Raised at login time on purpose, so a wrong token scope is diagnosed
 * immediately instead of surfacing later as an opaque JMAP error.
 *
 * The message is deliberately free of anything derived from the token.
 */
class MaskedEmailScopeMissingException : Exception("API token has no Masked Email scope")
