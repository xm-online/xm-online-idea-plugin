package com.icthh.xm.service

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.shared.showNotification
import com.icthh.xm.utils.templateOrEmpty
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.project.Project
import httpGetResponse
import httpGetString
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.EMPTY_REQUEST
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.apache.http.HttpHeaders.CONTENT_TYPE
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.entity.ContentType.*
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import java.io.InputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class ExternalConfigService {

    private val tokens: MutableMap<String, TokenResponse> = ConcurrentHashMap()

    val objectMapper = ObjectMapper()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(KotlinModule.Builder().build())

    fun getConfigFile(project: Project, env: EnvironmentSettings, path: String, version: String? = env.version): String {
        return getConfigFileIfExists(project, env, path, version) ?: throw NotFoundException()
    }

    fun getConfigFileIfExists(project: Project, env: EnvironmentSettings, path: String, version: String? = env.version): String? {
        val baseUrl = env.xmUrl
        val accessToken = getToken(env)
        val url = baseUrl + "/config/api/config/tenants${path}?${version.templateOrEmpty{"version=${it}"}}"
        url.httpGetResponse(mapOf(AUTHORIZATION to "Bearer $accessToken")).use {response ->

            if (response.code == 404) {
                return null
            }
            if (response.code != 200) {
                project.showNotification("Get configuration", "Error get configurations", ERROR) {
                    "${response.code} ${response.message} Url: $url"
                }
                throw RuntimeException(response.message)
            }
            return response.body?.byteString()?.utf8()
        }
    }

    fun getToken(env: EnvironmentSettings): String {
        val cachedToken = tokens[env.id]
        if (cachedToken != null && cachedToken.expireTime > System.currentTimeMillis()) {
            return cachedToken.access_token
        }

        val tokenResponse = fetchToken(env)
        tokens[env.id] = tokenResponse
        return tokenResponse.access_token
    }

    fun fetchToken(env: EnvironmentSettings): TokenResponse {
        val clientToken = "${env.clientId}:${env.clientPassword}".toByteArray()
        val formBody: RequestBody = FormBody.Builder()
            .addEncoded("grant_type", "password")
            .addEncoded("username", env.xmSuperAdminLogin)
            .addEncoded("password", env.xmSuperAdminPassword)
            .build()
        OkHttpClient().newCall(
            Request.Builder()
                .url(env.xmUrl + "/uaa/oauth/token")
                .post(formBody)
                .addHeader(AUTHORIZATION, "Basic ${Base64.getEncoder().encodeToString(clientToken)}")
                .addHeader(CONTENT_TYPE, APPLICATION_FORM_URLENCODED.toString())
                .build()
        ).execute().use {
            return objectMapper.readValue<TokenResponse>(it.body?.byteString()?.utf8() ?: "")
        }
    }

    infix fun String.to1(value: String) = BasicNameValuePair(this, value)

    fun refresh(env: EnvironmentSettings) {
        OkHttpClient().newCall(
            Request.Builder()
                .url(env.xmUrl + "/config/api/profile/refresh")
                .post(EMPTY_REQUEST)
                .addHeader(AUTHORIZATION, "bearer ${getToken(env)}")
                .addHeader(CONTENT_TYPE, APPLICATION_JSON.toString())
                .build()
        ).execute().use {
            it.body?.byteString()?.utf8()
        }
    }

    fun getCurrentVersion(env: EnvironmentSettings): String {
        val accessToken = getToken(env)
        return (env.xmUrl + "/config/api/version").httpGetString(mapOf(AUTHORIZATION to "bearer $accessToken")) ?: "-"
    }

    fun updateInMemory(project: Project, env: EnvironmentSettings, files: Map<String, InputStream?>) {
        HttpClients.createDefault().use { httpclient ->

            var builder = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
            files.forEach{
                builder = builder.addBinaryBody("files", it.value, DEFAULT_BINARY, it.key)
            }
            val data = builder.build()

            val request = RequestBuilder
                .post("${project.getSettings().selected()?.xmUrl}/config/api/inmemory/config")
                .setHeader(AUTHORIZATION, "bearer ${getToken(env)}")
                .setEntity(data)
                .build()

            val response = httpclient.execute(request)

            if (response.statusLine.statusCode != 200) {
                project.showNotification("Refresh", "Error update configurations", ERROR) {
                    "${response.statusLine.statusCode} ${response.statusLine.reasonPhrase}"
                }
                throw RuntimeException("Error update configuration")
            }
        }
    }

    fun deleteConfig(project: Project, env: EnvironmentSettings, paths: Set<String>) {
        HttpClients.createDefault().use { httpclient ->
            val request = RequestBuilder
                .delete("${project.getSettings().selected()?.xmUrl}/config/api/inmemory/config/tenants/XM")
                .setHeader(AUTHORIZATION, "bearer ${getToken(env)}")
                .setHeader(CONTENT_TYPE, APPLICATION_JSON.mimeType)
                .setEntity(StringEntity(objectMapper.writeValueAsString(paths)))
                .build()

            val response = httpclient.execute(request)

            if (response.statusLine.statusCode != 200) {
                project.showNotification("Refresh", "Error delete configurations", ERROR) {
                    "${response.statusLine.statusCode} ${response.statusLine.reasonPhrase}"
                }
                throw RuntimeException("Error delete configuration")
            }
        }
    }

    fun updateFileInMemory(project: Project, env: EnvironmentSettings, path: String, content: String) {
        val baseUrl = env.xmUrl

        val body: RequestBody = content.toRequestBody(
            TEXT_PLAIN.toString().toMediaTypeOrNull()
        )

        val request: Request = Request.Builder()
            .url(baseUrl + "/config/api/inmemory${path}")
            .addHeader(AUTHORIZATION, "bearer ${getToken(env)}")
            .post(body)
            .build()
        OkHttpClient().newCall(request).execute().use {  response ->
            if (response.code != 200) {
                project.showNotification("Refresh", "Error update configurations", ERROR) {
                    "${response.code} ${response.message}"
                }
                throw RuntimeException("Error update configuration")
            }
        }

    }
}

data class TokenResponse(val access_token: String, val expires_in: Int) {
    val expireTime: Long = System.currentTimeMillis() + (expires_in - 60) * 1000
}

class NotFoundException: RuntimeException()
