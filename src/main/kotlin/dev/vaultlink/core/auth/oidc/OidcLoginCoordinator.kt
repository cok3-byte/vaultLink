package dev.vaultlink.core.auth.oidc

import com.intellij.ide.BrowserUtil
import dev.vaultlink.core.auth.AuthResult
import dev.vaultlink.core.auth.LoginException
import dev.vaultlink.core.auth.requireLoginSuccess
import dev.vaultlink.core.auth.runLoginRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Orchestrates auth_url -> browser -> local callback -> token exchange.
 *
 * NOTE: the design calls for running this flow inside a cancelable `Task.Backgroundable` with
 * progress ("Waiting for authentication in the browser...") and a timeout — that's wired up by
 * the caller (VaultProjectService), not here, to keep this class free of UI dependencies beyond
 * BrowserUtil.
 */
class OidcLoginCoordinator(
    private val vaultUrl: String,
    private val oidcMountPath: String,
    private val oidcRole: String,
    private val callbackPort: Int,
    private val callbackPath: String,
    private val httpClient: HttpClient,
    private val loginTimeoutSeconds: Long = 180,
) {
    fun login(): AuthResult {
        val state = UUID.randomUUID().toString()
        val redirectUri = "http://127.0.0.1:$callbackPort$callbackPath"
        val authUrl = requestAuthUrl(state, redirectUri)

        val callbackServer = OidcCallbackServer(callbackPort, callbackPath)
        val callbackFuture = callbackServer.start()
        try {
            BrowserUtil.browse(authUrl)
            val callback = try {
                callbackFuture.get(loginTimeoutSeconds, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                throw LoginException.TimedOut("Login in the browser did not complete in time", e)
            } catch (e: ExecutionException) {
                throw LoginException.Unexpected(e.cause?.message ?: "Invalid OIDC callback", e.cause ?: e)
            }
            if (callback.state != state) {
                throw LoginException.Unexpected("The OIDC callback's 'state' does not match (possible CSRF)")
            }
            return exchangeCallback(callback)
        } finally {
            callbackServer.stop()
        }
    }

    private fun requestAuthUrl(state: String, redirectUri: String): String = runLoginRequest {
        val payload = """{"role":"$oidcRole","redirect_uri":"$redirectUri"}"""
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$vaultUrl/v1/auth/$oidcMountPath/oidc/auth_url"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(10))
            .method("PUT", HttpRequest.BodyPublishers.ofString(payload))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        requireLoginSuccess(response, "Invalid or rejected OIDC role")
        val data = Json.parseToJsonElement(response.body()).jsonObject["data"]!!.jsonObject
        data["auth_url"]!!.jsonPrimitive.content
    }

    private fun exchangeCallback(callback: OidcCallbackResult): AuthResult = runLoginRequest {
        val nonceParam = callback.nonce?.let { "&nonce=$it" } ?: ""
        val uri = "$vaultUrl/v1/auth/$oidcMountPath/oidc/callback" +
            "?state=${callback.state}$nonceParam&code=${callback.code}"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        requireLoginSuccess(response, "Vault rejected the OIDC callback")
        val auth = Json.parseToJsonElement(response.body()).jsonObject["auth"]!!.jsonObject
        AuthResult(
            clientToken = auth["client_token"]!!.jsonPrimitive.content,
            leaseDuration = auth["lease_duration"]!!.jsonPrimitive.content.toLong(),
            renewable = auth["renewable"]!!.jsonPrimitive.content.toBoolean(),
            issuedAt = Instant.now(),
        )
    }
}
