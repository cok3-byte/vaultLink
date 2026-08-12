package dev.vaultlink.core.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * POST /v1/auth/token/renew-self — generic endpoint, independent of the method that issued
 * the token; shared by all 4 auth strategies.
 */
fun renewSelf(vaultUrl: String, httpClient: HttpClient, clientToken: String): AuthResult {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("$vaultUrl/v1/auth/token/renew-self"))
        .header("X-Vault-Token", clientToken)
        .timeout(Duration.ofSeconds(10))
        .POST(HttpRequest.BodyPublishers.noBody())
        .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() in 200..299) { "Could not renew the token (${response.statusCode()})" }
    val auth = Json.parseToJsonElement(response.body()).jsonObject["auth"]!!.jsonObject
    return AuthResult(
        clientToken = auth["client_token"]!!.jsonPrimitive.content,
        leaseDuration = auth["lease_duration"]!!.jsonPrimitive.content.toLong(),
        renewable = auth["renewable"]!!.jsonPrimitive.content.toBoolean(),
        issuedAt = Instant.now(),
    )
}
