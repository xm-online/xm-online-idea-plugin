package com.icthh.xm.xmeplugin.utils

import com.icthh.xm.xmeplugin.yaml.YamlContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.ConcurrentHashMap
import com.oracle.truffle.js.runtime.JSContextOptions
import org.graalvm.polyglot.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.VarHandle
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.concurrent.getOrSet


val Project.jsRunner get() = this.service<GraalJsRunner>()

@Service(Service.Level.PROJECT)
class GraalJsRunner: Disposable {

    private val engine: Engine = Engine.newBuilder()
        .option("engine.WarnInterpreterOnly", "false")
        .allowExperimentalOptions(true)
        .build()

    private val executionContexts = ConcurrentHashMap<String, Context>()
    private val scripts = ConcurrentHashMap<String, ThreadLocal<Value>>()
    private val executionContext = ThreadLocal<Context>()

    fun runJsCondition(function: String, template: String, context: YamlContext): Boolean {
        return runJsScript(function, "return $template;", context).asBoolean()
    }

    fun runJsTemplate(function: String, template: String, context: YamlContext): String? {
        return runJsScript(function, "return `$template`;", context).asString()
    }

    fun runJsScript(function: String, sourceCode: String, context: YamlContext): Value {
        val scriptTl = scripts.computeIfAbsent(sourceCode) { ThreadLocal() }
        val script = scriptTl.getOrSet { createScript(function + "\n" + sourceCode) }
        return script.execute(context)
    }

    fun runJsScriptWithResult(function: String, sourceCode: String, context: YamlContext): Any? {
        val scriptTl = scripts.computeIfAbsent(sourceCode) { ThreadLocal() }
        val script = scriptTl.getOrSet { createScript(function + "\n" + sourceCode) }
        val ctx = executionContext.getOrSet { buildContext() }
        val result = script.execute(context)
        return copyToJavaLand(result, ctx)
    }

    private fun createScript(sourceCode: String): Value {
        val ctx = executionContext.getOrSet { buildContext() }
        return ctx.eval("js", """(context) => { ${sourceCode} }""")
    }

    private fun buildContext(): Context {
        var builder: Context.Builder = Context.newBuilder()
            .engine(engine)
            .allowIO(true)
            .allowExperimentalOptions(true)
            .allowHostAccess(
                HostAccess.newBuilder()
                    .allowAccessInheritance(true)
                    .allowAllClassImplementations(true)
                    .allowAllImplementations(true)
                    .allowBufferAccess(true)
                    .allowPublicAccess(true)
                    .allowMapAccess(true)
                    .allowIterableAccess(true)
                    .allowIteratorAccess(true)
                    .allowArrayAccess(true)
                    .allowListAccess(true)
                    .allowAllImplementations(true)
                    .denyAccess(Class::class.java)
                    .denyAccess(Field::class.java)
                    .denyAccess(Method::class.java)
                    .denyAccess(VarHandle::class.java)
                    .denyAccess(MethodHandle::class.java)
                    .build()
            ).allowHostClassLookup { true }

        builder = builder
            .option(JSContextOptions.UNHANDLED_REJECTIONS_NAME, "throw")
            .option(JSContextOptions.FOREIGN_OBJECT_PROTOTYPE_NAME, "true")
            .option(JSContextOptions.FOREIGN_HASH_PROPERTIES_NAME, "true")
        return builder.build()
    }

    override fun dispose() {
        executionContexts.values.forEach { it.close() }
        engine.close()
    }

    private fun copyToJavaLand(result: Any, context: Context): Any? {
        val value = if (result !is Value) {
            context.asValue(result)
        } else {
            result
        }

        if (value.isBoolean) {
            return value.asBoolean()
        } else if (value.isString) {
            return value.asString()
        } else if (value.isNumber) {
            if (value.fitsInInt()) {
                return value.asInt()
            } else if (value.fitsInLong()) {
                return value.asLong()
            } else if (value.fitsInDouble()) {
                return value.asDouble()
            }
            return value.`as`(Number::class.java)
        } else if (value.isDate) {
            return value.asDate()
        } else if (value.isDuration) {
            return value.asDuration()
        } else if (value.isTime) {
            return value.asTime()
        } else if (value.isTimeZone) {
            return value.asTimeZone()
        } else if (value.isInstant) {
            return value.asInstant()
        } else if (value.isException) {
            return value.throwException()
        } else if (value.isHostObject) {
            return value.asHostObject()
        } else if (value.isProxyObject) {
            return value.asProxyObject()
        } else if (value.hasArrayElements()) {
            val list: MutableList<Any?> = ArrayList()
            for (i in 0..<value.arraySize) {
                list.add(copyToJavaLand(value.getArrayElement(i), context))
            }
            return list
        } else if (value.hasMembers()) {
            val map: MutableMap<String, Any?> = HashMap()
            value.memberKeys.forEach{ key: String ->
                map[key] = copyToJavaLand(value.getMember(key), context)
            }
            return map
        } else if (value.isNull) {
            return null
        }

        throw IllegalArgumentException("Cannot copy value $value.")
    }


}
