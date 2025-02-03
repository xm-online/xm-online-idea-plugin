package com.icthh.xm.xmeplugin.yaml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.icthh.xm.xmeplugin.utils.log
import com.icthh.xm.xmeplugin.utils.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.apache.commons.lang3.time.StopWatch
import java.util.concurrent.TimeUnit

class ReadConfigFileStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val configYaml = this::class.java.classLoader.getResource("specs/xme-plugin.yml")?.readText() ?: return
        val pluginConfig: XmePluginSpec = ObjectMapper(YAMLFactory()).registerKotlinModule().readValue(configYaml)
        log.info("Plugin config: $pluginConfig")
        project.xmePluginSpecService.initByEmbedConfig(pluginConfig)
    }

}
