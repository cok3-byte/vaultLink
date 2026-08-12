package dev.vaultlink.core.auth.strategies

import dev.vaultlink.core.auth.AuthResult
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

/** Shared by LDAP and Userpass: both use POST /v1/auth/{mount}/login/{username}. */
internal fun usernamePasswordLogin(
    vaultUrl: String,
    mount: String,
    username: String,
    password: String,
    httpClient: HttpClient,
): AuthResult = runLoginRequest {
    val payload = """{"password":"${password.escapeJson()}"}"""
    val request = HttpRequest.newBuilder()
        .uri(URI.create("$vaultUrl/v1/auth/$mount/login/$username"))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(10))
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    requireLoginSuccess(response, "Incorrect username or password")
    val auth = Json.parseToJsonElement(response.body()).jsonObject["auth"]!!.jsonObject
    AuthResult(
        clientToken = auth["client_token"]!!.jsonPrimitive.content,
        leaseDuration = auth["lease_duration"]!!.jsonPrimitive.content.toLong(),
        renewable = auth["renewable"]!!.jsonPrimitive.content.toBoolean(),
        issuedAt = Instant.now(),
    )
}

private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")
