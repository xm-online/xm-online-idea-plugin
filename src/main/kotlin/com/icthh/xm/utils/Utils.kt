package com.icthh.xm.utils

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap

val loggers = ConcurrentHashMap<Class<Any>, Logger>()
val loggerFactory: (Class<Any>) -> Logger = { Logger.getInstance(it) }
val Any.log: Logger get() = loggers.computeIfAbsent(this.javaClass, loggerFactory)
