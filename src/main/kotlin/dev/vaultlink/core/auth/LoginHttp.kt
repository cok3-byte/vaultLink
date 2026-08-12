package dev.vaultlink.core.auth

import java.io.IOException
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException

/** Wraps a login HTTP call, mapping network/timeout exceptions to [LoginException]. */
internal fun <T> runLoginRequest(block: () -> T): T = try {
    block()
} catch (e: LoginException) {
    throw e
} catch (e: HttpTimeoutException) {
    throw LoginException.TimedOut("Timed out contacting Vault", e)
} catch (e: IOException) {
    throw LoginException.ConnectionError("Could not contact the Vault server", e)
} catch (e: Exception) {
    throw LoginException.Unexpected(e.message ?: "Unexpected error", e)
}

/** Throws the appropriate [LoginException] based on the status code if the response wasn't 2xx. */
internal fun requireLoginSuccess(response: HttpResponse<String>, failedMessage: String) {
    val code = response.statusCode()
    if (code in 200..299) return
    throw when (code) {
        400, 401, 403 -> LoginException.Failed(failedMessage)
        in 500..599 -> LoginException.ConnectionError("Vault returned a server error ($code)")
        else -> LoginException.Unexpected("Vault returned an unexpected error ($code)")
    }
}
