package com.icthh.xm.service

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.actions.settings.EnvironmentSettings
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.apache.http.HttpHeaders.CONTENT_TYPE
import org.apache.http.client.fluent.Request.Get
import org.apache.http.client.fluent.Request.Post
import org.apache.http.entity.ContentType.APPLICATION_FORM_URLENCODED
import org.apache.http.message.BasicNameValuePair
import kotlin.text.Charsets.UTF_8

class ExternalConfigService {

    val objectMapper = ObjectMapper()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(KotlinModule())

    fun getConfigFile(env: EnvironmentSettings, path: String): String {
        var baseUrl = env.xmUrl.trim('/')
        val content = Post(baseUrl + "/uaa/oauth/token")
            .bodyForm(
                "grant_type" to "password",
                "username" to env.xmSuperAdminLogin,
                "password" to env.xmSuperAdminPassword
            )
            .addHeader(AUTHORIZATION, "Basic d2ViYXBwOndlYmFwcA==")
            .addHeader(CONTENT_TYPE, APPLICATION_FORM_URLENCODED.toString()).execute().returnContent().asString()

        val tokenResponse = objectMapper.readValue<TokenResponse>(content)

        val configFile = Get(baseUrl + "/config/api/config/tenants" + path)
            .addHeader(AUTHORIZATION, "bearer ${tokenResponse.access_token}")
            .execute().returnContent().asString(UTF_8)
        return configFile
    }

    infix fun String.to(value: String) = BasicNameValuePair(this, value)

}

data class TokenResponse(val access_token: String)
