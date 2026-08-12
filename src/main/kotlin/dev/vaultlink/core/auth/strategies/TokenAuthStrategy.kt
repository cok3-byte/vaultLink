package dev.vaultlink.core.auth.strategies

import dev.vaultlink.core.auth.AuthMethod
import dev.vaultlink.core.auth.AuthResult
import dev.vaultlink.core.auth.VaultAuthStrategy
import dev.vaultlink.core.auth.renewSelf
import dev.vaultlink.core.auth.requireLoginSuccess
import dev.vaultlink.core.auth.runLoginRequest
import dev.vaultlink.core.auth.ui.TokenLoginDialog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/** There's no login endpoint: the pasted token is used directly as X-Vault-Token on every call. */
class TokenAuthStrategy(
    private val vaultUrl: String,
    private val httpClient: HttpClient,
) : VaultAuthStrategy {

    override val method = AuthMethod.TOKEN

    override fun authenticate(): AuthResult = lookupSelf(TokenLoginDialog.promptOrThrow())

    override fun renew(clientToken: String): AuthResult = renewSelf(vaultUrl, httpClient, clientToken)

    override fun supportsRenewal(): Boolean = true // depends on whether the pasted token is renewable

    private fun lookupSelf(token: String): AuthResult = runLoginRequest {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$vaultUrl/v1/auth/token/lookup-self"))
            .header("X-Vault-Token", token)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        requireLoginSuccess(response, "Invalid or expired token")
        val data = Json.parseToJsonElement(response.body()).jsonObject["data"]!!.jsonObject
        AuthResult(
            clientToken = token,
            leaseDuration = data["ttl"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
            renewable = data["renewable"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            issuedAt = Instant.now(),
        )
    }
}
