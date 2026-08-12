package dev.vaultlink.core.vault.exception

sealed class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Auth(message: String, cause: Throwable? = null) : VaultException(message, cause)
    class Network(message: String, cause: Throwable? = null) : VaultException(message, cause)
    class NotFound(message: String) : VaultException(message)
    class PermissionDenied(message: String) : VaultException(message)
}
