package com.fastmask.domain.auth

/**
 * The API token authenticated successfully but carries no Masked Email scope,
 * so no masked-address call can ever succeed with it.
 *
 * Raised at login time on purpose. Previously the session lookup fell back to
 * "whatever account came first", login appeared to succeed, and the user only
 * hit an opaque JMAP error later — with no way to guess that the token scope
 * was the problem.
 *
 * The message is deliberately free of anything derived from the token.
 */
class MaskedEmailScopeMissingException : Exception("API token has no Masked Email scope")
