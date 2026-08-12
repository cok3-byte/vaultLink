package dev.vaultlink.core.net

import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/** Only allows adding a custom CA; never offers a "skip TLS verification" option. */
object CustomTlsSocketFactory {

    fun buildSslContext(customCaCertPath: String?): SSLContext {
        val sslContext = SSLContext.getInstance("TLS")
        if (customCaCertPath == null) {
            sslContext.init(null, null, null)
            return sslContext
        }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        FileInputStream(customCaCertPath).use { input ->
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(input)
            keyStore.setCertificateEntry("vault-custom-ca", cert)
        }

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        sslContext.init(null, trustManagerFactory.trustManagers, null)
        return sslContext
    }
}
