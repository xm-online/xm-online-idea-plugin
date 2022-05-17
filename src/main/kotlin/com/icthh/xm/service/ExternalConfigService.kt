package com.icthh.xm.service

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.shared.showNotification
import com.icthh.xm.utils.log
import com.icthh.xm.utils.readTextAndClose
import com.icthh.xm.utils.templateOrEmpty
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.project.Project
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.apache.http.HttpHeaders.CONTENT_TYPE
import org.apache.http.client.fluent.Request.*
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
        val response = Get(url)
            .addHeader(AUTHORIZATION, "bearer $accessToken")
            .execute()

        return response.handleResponse {
            val returnResponse = response.returnResponse()
            if (returnResponse.statusLine.statusCode == 404) {
                return@handleResponse null
            }
            if (returnResponse.statusLine.statusCode != 200) {
                project.showNotification("Get configuration", "Error get configurations", ERROR) {
                    "${returnResponse.statusLine.statusCode} ${returnResponse.statusLine.reasonPhrase} Url: $url"
                }
                throw RuntimeException(returnResponse.statusLine.reasonPhrase)
            }
            returnResponse.entity.content.readTextAndClose()
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
        val content = Post(env.xmUrl + "/uaa/oauth/token")
            .bodyForm(
                "grant_type" to "password",
                "username" to env.xmSuperAdminLogin,
                "password" to env.xmSuperAdminPassword
            )
            .addHeader(AUTHORIZATION, "Basic ${Base64.getEncoder().encodeToString(clientToken)}")
            .addHeader(CONTENT_TYPE, APPLICATION_FORM_URLENCODED.toString()).execute().returnContent().asString()

        return objectMapper.readValue<TokenResponse>(content)
    }

    infix fun String.to(value: String) = BasicNameValuePair(this, value)

    fun refresh(env: EnvironmentSettings) {
        Post(env.xmUrl + "/config/api/profile/refresh")
            .addHeader(AUTHORIZATION, "bearer ${getToken(env)}")
            .execute().returnResponse()
    }

    fun getCurrentVersion(env: EnvironmentSettings): String {
        val accessToken = getToken(env)
        return Get(env.xmUrl + "/config/api/version")
            .addHeader(AUTHORIZATION, "bearer $accessToken")
            .execute().returnContent().asString()
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

        val response = Put(baseUrl + "/config/api/inmemory${path}")
            .addHeader(AUTHORIZATION, "bearer ${getToken(env)}")
            .bodyString(content, TEXT_PLAIN)
            .execute().returnResponse()

        if (response.statusLine.statusCode != 200) {
            project.showNotification("Refresh", "Error update configurations", ERROR) {
                "${response.statusLine.statusCode} ${response.statusLine.reasonPhrase}"
            }
            throw RuntimeException("Error update configuration")
        }
    }
}

data class TokenResponse(val access_token: String, val expires_in: Int) {
    val expireTime: Long = System.currentTimeMillis() + (expires_in - 60) * 1000
}

class NotFoundException: RuntimeException()
