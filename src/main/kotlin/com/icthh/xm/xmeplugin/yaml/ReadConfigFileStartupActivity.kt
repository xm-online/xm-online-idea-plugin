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

    val specFiles = listOf("xme-plugin.yml", "scheduler-spec.yml")

    override suspend fun execute(project: Project) {
        var config = XmePluginSpec()
        specFiles.forEach {
            val configYaml = this::class.java.classLoader.getResource("specs/${it}")?.readText() ?: return
            val pluginConfig: XmePluginSpec = ObjectMapper(YAMLFactory()).registerKotlinModule().readValue(configYaml)
            config = joinSpec(config, pluginConfig)
        }
        log.info("Plugin config: $config")
        project.xmePluginSpecService.initByEmbedConfig(config)
    }

}
