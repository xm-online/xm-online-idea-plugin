package com.icthh.xm.xmeplugin.utils

import com.jetbrains.rd.util.ConcurrentHashMap
import io.ktor.util.*
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import javax.net.ssl.*

val filecache = ConcurrentHashMap<String, String>()
fun downloadFileContent(urlString: String): String {
    return filecache.getOrPut(urlString) {
        val fileContext = readUrlContentInternal(urlString)
        val fileName = DigestUtils.sha256Hex(urlString.encodeBase64() + fileContext.hashCode()) + ".json"
        val path = "/tmp/$fileName"
        File(path).writeText(fileContext)
        path
    }
}

private fun readUrlContentInternal(urlString: String): String {

    val url = URI(urlString).toURL()
    if (url.protocol.equals("https", ignoreCase = true)) {
        disableHttpsCertificateValidation()
    }
    if (url.protocol.startsWith("http", ignoreCase = true)) {
        val connection = url.openConnection()
        if (connection is HttpURLConnection) {
            connection.instanceFollowRedirects = false
            val responseCode = connection.responseCode
            if (responseCode in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                return readUrlContentInternal(location)
            }
        }
        return connection.getInputStream().bufferedReader().use { it.readText() }
    }
    // Open the URL stream and read the entire content as text
    return url.openStream().bufferedReader().use { it.readText() }
}

/**
 * Disables HTTPS certificate validation by installing a trust manager that trusts all certificates
 * and a hostname verifier that accepts any hostname.
 */
fun disableHttpsCertificateValidation() {
    // Create a trust manager that does not validate certificate chains.
    val trustAllCerts: Array<TrustManager> = arrayOf(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()

        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) { /* No-op */ }

        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) { /* No-op */ }
    })

    try {
        // Install the all-trusting trust manager
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)

        // Install a hostname verifier that always returns true
        HttpsURLConnection.setDefaultHostnameVerifier(HostnameVerifier { _, _ -> true })
    } catch (e: Exception) {
        throw RuntimeException("Failed to disable HTTPS certificate validation", e)
    }
}

