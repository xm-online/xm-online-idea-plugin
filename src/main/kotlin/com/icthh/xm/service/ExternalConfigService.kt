package com.icthh.xm.service

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.utils.readTextAndClose
import org.apache.commons.net.nntp.NNTPCommand.POST
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.apache.http.HttpHeaders.CONTENT_TYPE
import org.apache.http.client.fluent.Request.Get
import org.apache.http.client.fluent.Request.Post
import org.apache.http.entity.ContentType.APPLICATION_FORM_URLENCODED
import org.apache.http.message.BasicNameValuePair

class ExternalConfigService {

    val objectMapper = ObjectMapper()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(KotlinModule())

    fun getConfigFile(env: EnvironmentSettings, path: String): String {
        val baseUrl = env.xmUrl
        val accessToken = getToken(env)

        val response = Get(baseUrl + "/config/api/config/tenants" + path)
            .addHeader(AUTHORIZATION, "bearer $accessToken")
            .execute()

        return response.handleResponse {
            val returnResponse = response.returnResponse()
            if (returnResponse.statusLine.statusCode == 404) {
                throw NotFoundException();
            }
            returnResponse.entity.content.readTextAndClose()
        }
    }

    fun getToken(env: EnvironmentSettings): String {
        val content = Post(env.xmUrl + "/uaa/oauth/token")
            .bodyForm(
                "grant_type" to "password",
                "username" to env.xmSuperAdminLogin,
                "password" to env.xmSuperAdminPassword
            )
            .addHeader(AUTHORIZATION, "Basic d2ViYXBwOndlYmFwcA==")
            .addHeader(CONTENT_TYPE, APPLICATION_FORM_URLENCODED.toString()).execute().returnContent().asString()

        val tokenResponse = objectMapper.readValue<TokenResponse>(content)
        return tokenResponse.access_token
    }

    infix fun String.to(value: String) = BasicNameValuePair(this, value)

    fun refresh(env: EnvironmentSettings) {
        Post(env.xmUrl + "/config/api/profile/refresh")
            .addHeader(AUTHORIZATION, "bearer ${getToken(env)}")
            .execute().returnResponse()
    }

}

data class TokenResponse(val access_token: String)

class NotFoundException: RuntimeException()
