package com.icthh.xm.xmeplugin.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.diagnostic.thisLogger

val YAML_MAPPER = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

inline val Any.log get() = thisLogger()

val Boolean?.isTrue get(): Boolean = this == true

fun String?.templateOrEmpty(template: (String) -> String): String {
    this ?: return ""
    return template.invoke(this)
}
