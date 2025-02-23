package com.icthh.xm.xmeplugin.yaml

import com.icthh.xm.xmeplugin.utils.AntPathMatcher

// Top-level configuration object
class XmePluginSpec {
    var specifications: MutableList<Specification> = mutableListOf()
    var jsFunctions: MutableList<JsFunction> = mutableListOf()
}

private val antMatcher = AntPathMatcher()

class Specification (
    val key: String,
    var jsonSchemaUrl: String? = null,
    private var fileAntPatterns: List<String> = mutableListOf(),
    var inspections: List<Inspection> = mutableListOf(),
    var references: List<ReferenceEntry> = mutableListOf(),
    var injections: List<LanguageInjection> = mutableListOf(),
    var autocompletes: List<AutoComplete> = mutableListOf(),
    var actions: List<Action> = mutableListOf()
) {
    fun matchPath(path: String): Boolean {
        return fileAntPatterns.any { antMatcher.match("/" + it.trimStart('/'), path) }
    }
}

class AutoComplete {
    var elementPath: String? = null
    var variants: List<String>? = null
    var variantsPath: String? = null
    var variantsExpression: String? = null
    var includeFunctions: List<String>? = null
}

class LanguageInjection {
    var language: String? = null
    var elementPath: String? = null
    var jsonSchemaUrl: String? = null
}

open class Action {
    var key: String? = null
    var nameTemplate: String? = null

    var elementPath: String? = null // optional
    var condition: String? = null // optional

    var action: String? = null
    var includeFunctions: List<String>? = null

    var successMessageTemplate: String? = null
    var successActions: List<SuccessAction> = mutableListOf()
    open class SuccessAction {
        var action: String? = null
        var actionNameTemplate: String? = null
    }
}

open class LocalInspection {
    var key: String? = null
    var elementPath: String? = null
    var errorMessageTemplate: String? = null
    var severity: Severity? = null
    var includeFunctions: List<String>? = null
    var action: String? = null
    var actionMessageTemplate: String? = null
}

// Represents an inspection definition
class Inspection: LocalInspection() {
    var condition: String? = null
}

// An enum for severity levels
enum class Severity {
    INFO, WARN, ERROR
}

// Represents a reference entry in the "references" list
class ReferenceEntry: LocalInspection() {
    var reference: ReferenceDetail? = null
}

// Represents the "reference" sub-object
class ReferenceDetail {
    var type: String? = null
    var filePathTemplates: List<String>? = null
    var elementPathTemplates: List<String>? = null
    var required: Boolean? = null
}

// Represents a JavaScript function definition under "jsFunction"
class JsFunction {
    var key: String? = null
    var body: String? = null
}


