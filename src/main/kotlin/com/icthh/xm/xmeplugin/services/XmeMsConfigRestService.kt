package com.icthh.xm.xmeplugin.services

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.xmeplugin.domain.ChangesFiles
import com.icthh.xm.xmeplugin.services.settings.EnvironmentSettings
import com.icthh.xm.xmeplugin.utils.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import org.apache.commons.codec.digest.DigestUtils.sha256Hex
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.apache.http.HttpHeaders.CONTENT_TYPE
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.entity.ContentType.*
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import readTextAndClose
import java.io.InputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

val Project.configRestService get() = this.service<XmeMsConfigRestService>()

@Service(Service.Level.PROJECT)
class XmeMsConfigRestService {

    private val tokens: MutableMap<String, TokenResponse> = ConcurrentHashMap()

    private val objectMapper = ObjectMapper()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(KotlinModule.Builder().build())

    fun getConfigFileIfExists(project: Project, env: EnvironmentSettings, path: String, version: String? = env.version): String? {
        val baseUrl = env.xmUrl
        val accessToken = getToken(env)
        val url = baseUrl + "/config/api${path}?${version.templateOrEmpty{"version=${it}"}}"
        url.httpGetResponse(mapOf(AUTHORIZATION to "Bearer $accessToken")).use {response ->

            if (response.code == 404) {
                return null
            }
            if (response.code != 200) {
                project.showErrorNotification("Error get configurations") {
                    "${response.code} ${response.message} Url: $url"
                }
                return null
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
                project.showErrorNotification("Error update configurations") {
                    "${response.statusLine.statusCode} ${response.statusLine.reasonPhrase}"
                }
                throw ErrorUpdateConfiguration("Error update configuration")
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
                project.showErrorNotification("Error delete configurations") {
                    "${response.statusLine.statusCode} ${response.statusLine.reasonPhrase}"
                }
                throw ErrorUpdateConfiguration("Error delete configuration")
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
                project.showErrorNotification("Error update configurations") {
                    "${response.code} ${response.message}"
                }
                throw ErrorUpdateConfiguration("Error update configuration")
            }
        }

    }

    fun updateFilesInMemory(project: Project, changesFiles: ChangesFiles, selected: EnvironmentSettings): Future<*> {
        return project.doAsync {
            selected.lastChangedFiles.clear()
            selected.lastChangedFiles.addAll(changesFiles.editedInThisIteration)
            selected.lastChangedFiles.removeAll(selected.ignoredFiles)
            invokeLater { project.save() }

            val regularUpdateFiles = changesFiles.forRegularUpdate(selected.ignoredFiles)
            if (regularUpdateFiles.isNotEmpty()) {
                val map = changesFiles.updatedFileContent.filterKeys { it in regularUpdateFiles }
                project.configRestService.updateInMemory(project, selected, map)
            }
            val toDelete = changesFiles.toDelete(selected.ignoredFiles)
            if (toDelete.isNotEmpty()) {
                project.configRestService.deleteConfig(project, selected, toDelete)
            }
            changesFiles.getBigFilesForUpdate(selected.ignoredFiles).forEach {
                val inputStream = changesFiles.updatedFileContent[it]
                inputStream?.reset()
                val content = inputStream?.readTextAndClose() ?: ""
                project.configRestService.updateFileInMemory(project, selected, it, content)
            }
            project.showInfoNotification("Configs update") {
                "Configs successfully update"
            }
            project.saveCurrentFileStates(changesFiles)
        }
    }

    private fun Project.saveCurrentFileStates(changesFiles: ChangesFiles) {
        val settings = getSettings().selected() ?: return
        val editedFiles = HashMap<String, String>()
        changesFiles.updatedFiles().forEach {
            it.content.reset()
            val content = it.content.readTextAndClose().trim()
            val sha256Hex = sha256Hex(content)
            editedFiles[it.path] = sha256Hex
        }
        settings.lastChangedState = editedFiles
        invokeLater { save() }
    }
}

data class TokenResponse(val access_token: String, val expires_in: Int) {
    val expireTime: Long = System.currentTimeMillis() + (expires_in - 60) * 1000
}

class NotFoundException: RuntimeException()

class ErrorUpdateConfiguration(message: String): RuntimeException(message)
