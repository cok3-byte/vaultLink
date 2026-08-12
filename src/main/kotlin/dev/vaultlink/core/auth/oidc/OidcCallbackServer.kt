package dev.vaultlink.core.auth.oidc

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

data class OidcCallbackResult(val state: String, val nonce: String?, val code: String)

/**
 * Loopback server (com.sun.net.httpserver.HttpServer) that captures state+nonce+code from the
 * OIDC callback and shuts down immediately afterward. Default port/path 8250/oidc/callback, the
 * same one `vault login -method=oidc` uses from the CLI.
 */
class OidcCallbackServer(
    private val port: Int,
    private val path: String,
) {
    private var server: HttpServer? = null

    fun start(): CompletableFuture<OidcCallbackResult> {
        val future = CompletableFuture<OidcCallbackResult>()
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        httpServer.createContext(path) { exchange ->
            val params = parseQuery(exchange.requestURI.rawQuery)
            val state = params["state"]
            val code = params["code"]
            val body = if (state != null && code != null) {
                future.complete(OidcCallbackResult(state, params["nonce"], code))
                "Login successful, you can close this tab.".toByteArray()
            } else {
                future.completeExceptionally(IllegalStateException("Incomplete OIDC callback: missing state/code"))
                "Login failed: incomplete parameters.".toByteArray()
            }
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        httpServer.executor = Executors.newSingleThreadExecutor()
        httpServer.start()
        server = httpServer
        return future
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
            key to value
        }.toMap()
    }
}
