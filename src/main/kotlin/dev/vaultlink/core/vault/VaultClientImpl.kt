package dev.vaultlink.core.vault

import dev.vaultlink.core.net.RetryPolicy
import dev.vaultlink.core.vault.exception.VaultException
import dev.vaultlink.core.vault.model.VaultMount
import dev.vaultlink.core.vault.model.VaultSecretData
import dev.vaultlink.core.vault.model.VaultSecretMetadata
import dev.vaultlink.core.vault.model.VaultSecretVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Native java.net.http.HttpClient (no vault-java-driver or other bundled dependencies). */
class VaultClientImpl(
    private val vaultUrl: String,
    private val namespace: String?,
    private val httpClient: HttpClient,
    private val retryPolicy: RetryPolicy,
    private val tokenProvider: () -> String,
) : VaultClient {

    private val json = Json { ignoreUnknownKeys = true }

    override fun readSecret(mount: String, path: String, version: Int?): VaultSecretData {
        val query = version?.let { "?version=$it" } ?: ""
        val root = json.parseToJsonElement(request("/v1/$mount/data/$path$query")).jsonObject["data"]!!.jsonObject
        val data = root["data"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
        if (data.isEmpty()) {
            throw VaultException.NotFound(
                "Vault returned no keys for GET $vaultUrl/v1/$mount/data/$path$query — compare " +
                    "against 'vault kv get $mount/$path' (check path, version, and that the mount is KV v2)",
            )
        }
        return VaultSecretData(data = data, metadata = root["metadata"]!!.jsonObject.toVersion())
    }

    override fun readMetadata(mount: String, path: String): VaultSecretMetadata {
        val data = json.parseToJsonElement(request("/v1/$mount/metadata/$path")).jsonObject["data"]!!.jsonObject
        val versions = data["versions"]!!.jsonObject.entries.map { (versionKey, versionValue) ->
            versionValue.jsonObject.toVersion(versionKey.toInt())
        }
        return VaultSecretMetadata(
            currentVersion = data["current_version"]?.jsonPrimitive?.intOrNull ?: 0,
            versions = versions,
        )
    }

    override fun health(): Boolean = try {
        request("/v1/sys/health")
        true
    } catch (e: VaultException) {
        false
    }

    override fun listMounts(): List<VaultMount> {
        val data = json.parseToJsonElement(request("/v1/sys/mounts")).jsonObject["data"]!!.jsonObject
        return data.entries.mapNotNull { (mountPath, mountInfo) ->
            val info = mountInfo.jsonObject
            val type = info["type"]?.jsonPrimitive?.contentOrNull
            val kvVersion = info["options"]?.jsonObject?.get("version")?.jsonPrimitive?.contentOrNull
            if (type == "kv" && kvVersion == "2") VaultMount(mountPath.removeSuffix("/")) else null
        }
    }

    override fun listSecrets(mount: String, path: String): List<String> {
        val data = json.parseToJsonElement(list("/v1/$mount/metadata/$path")).jsonObject["data"]!!.jsonObject
        return data["keys"]!!.jsonArray.map { it.jsonPrimitive.content }
    }

    private fun JsonObject.toVersion(explicitVersion: Int? = null) = VaultSecretVersion(
        version = explicitVersion ?: this["version"]?.jsonPrimitive?.intOrNull ?: 0,
        createdTime = this["created_time"]?.jsonPrimitive?.contentOrNull ?: "",
        deletionTime = this["deletion_time"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() },
        destroyed = this["destroyed"]?.jsonPrimitive?.booleanOrNull ?: false,
    )

    private fun requestBuilder(path: String): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("$vaultUrl$path"))
            .header("X-Vault-Token", tokenProvider())
            .timeout(Duration.ofSeconds(30))
        namespace?.let { builder.header("X-Vault-Namespace", it) }
        return builder
    }

    private fun send(path: String, request: HttpRequest): String {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            in 200..299 -> response.body()
            401, 403 -> throw VaultException.PermissionDenied("Vault denied the request (${response.statusCode()})")
            404 -> throw VaultException.NotFound("Secret not found at $path")
            else -> throw VaultException.Network("Vault returned ${response.statusCode()} for $path")
        }
    }

    private fun request(path: String): String = retryPolicy.execute {
        send(path, requestBuilder(path).GET().build())
    }

    /** Vault's LIST verb, used for browsing mounts/secrets (`LIST /v1/$mount/metadata/$path`). */
    private fun list(path: String): String = retryPolicy.execute {
        send(path, requestBuilder(path).method("LIST", HttpRequest.BodyPublishers.noBody()).build())
    }
}
