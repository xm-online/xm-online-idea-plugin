package com.icthh.xm.utils

import com.intellij.openapi.diagnostic.Logger
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

val loggers = ConcurrentHashMap<Class<Any>, Logger>()
val loggerFactory: (Class<Any>) -> Logger = { Logger.getInstance(it) }
val Any.log: Logger get() = loggers.computeIfAbsent(this.javaClass, loggerFactory)

fun InputStream.readTextAndClose(charset: Charset = Charsets.UTF_8): String {
    return this.bufferedReader(charset).use { it.readText() }
}
