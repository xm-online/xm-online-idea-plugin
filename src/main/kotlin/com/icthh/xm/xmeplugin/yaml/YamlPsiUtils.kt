package com.icthh.xm.xmeplugin.yaml

import com.icthh.xm.xmeplugin.utils.psiElement
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.apache.commons.lang3.StringUtils
import org.jetbrains.yaml.psi.*

sealed class YamlPatternToken {
    data class Key(val key: String) : YamlPatternToken()
    data object Array : YamlPatternToken()
    data class Condition(val key: String, val expectedValue: String) : YamlPatternToken()
    data class ValueText(val text: String) : YamlPatternToken()
    data object Root : YamlPatternToken()
}

val array = YamlPatternToken.Array
fun key(key: String) = YamlPatternToken.Key(key)
fun condition(key: String, expectedValue: String) = YamlPatternToken.Condition(key, expectedValue)
fun value(text: String) = YamlPatternToken.ValueText(text)

fun yamlPattern(debugString: String, userScalar: Boolean, keys: MutableList<YamlPatternToken>): PsiElementPattern.Capture<out PsiElement> {
    val pattern = buildPattern(debugString, keys.reversed().iterator()) {
        if (userScalar) {
            psiElement<YAMLScalar>()
        } else {
            psiElement<PsiElement>()
        }
    }
    return pattern
}

fun findElement(psiFile: PsiFile?, pattern: ElementPattern<out PsiElement?>): List<PsiElement> {
    val result = mutableListOf<PsiElement>()
    PsiTreeUtil.processElements(psiFile, object : PsiElementProcessor<PsiElement?> {
        override fun execute(element: PsiElement): Boolean {
            if (pattern.accepts(element)) {
                result.add(element)
                return false
            }
            return true
        }
    })
    return result
}

fun findAllElements(psiFile: PsiFile?, pattern: ElementPattern<out PsiElement?>): List<PsiElement> {
    val result = mutableListOf<PsiElement>()
    PsiTreeUtil.processElements(psiFile, object : PsiElementProcessor<PsiElement?> {
        override fun execute(element: PsiElement): Boolean {
            if (pattern.accepts(element)) {
                result.add(element)
            }
            return true
        }
    })
    return result
}

private fun buildPattern(
    debugString: String,
    iterator: Iterator<YamlPatternToken>,
    current: () -> PsiElementPattern.Capture<out PsiElement>
): PsiElementPattern.Capture<out PsiElement> {
    if (!iterator.hasNext()) {
        return current.invoke()
    }
    val command = iterator.next()
    val pattern = when (command) {
        is YamlPatternToken.Key -> {
            current.invoke().withParent(
                psiElement<YAMLKeyValue>().withName(command.key).withParent(
                    buildPattern(debugString, iterator) {
                        psiElement<YAMLMapping>()
                    }
                )
            )
        }

        is YamlPatternToken.Array -> {
            current.invoke().withParent(
                psiElement<YAMLSequenceItem>().withParent(
                    buildPattern(debugString, iterator) {
                        psiElement<YAMLSequence>()
                    }
                )
            )
        }
        is YamlPatternToken.Condition -> {
            buildPattern(debugString, iterator) {
                current.invoke().with(object : PatternCondition<PsiElement>("withCondition[${command.key}=${command.expectedValue}]") {
                        override fun accepts(mapping: PsiElement, context: ProcessingContext): Boolean {
                            if (mapping !is YAMLMapping) {
                                return false
                            }
                            val value = mapping.keyValues.find { it.keyText == command.key }?.valueText
                            return value == command.expectedValue
                        }
                })
            }
        }
        is YamlPatternToken.ValueText -> {
            buildPattern(debugString, iterator) {
                current.invoke().withText(command.text)
            }
        }

        YamlPatternToken.Root -> current.invoke().withParent(
            psiElement<YAMLDocument>()
        )
    }
    return pattern
}

fun String.toPsiPattern(userScalar: Boolean): PsiElementPattern.Capture<out PsiElement> {
    val keys = mutableListOf<YamlPatternToken>(YamlPatternToken.Root)
    keys.addAll(parseDsl(this))
    return yamlPattern(this, userScalar, keys)
}

fun parseDsl(dsl: String): MutableList<YamlPatternToken> {
    val tokens = mutableListOf<YamlPatternToken>()
    // Split on '.' (assuming dots are not used within the special syntax)
    val segments = splitByDot(dsl)
    for (segment in segments) {
        // Handle array indicator, e.g. "types[]"
        if (segment.endsWith("[]")) {
            val key = segment.removeSuffix("[]")
            if (key.isNotEmpty()) {
                tokens.add(YamlPatternToken.Key(key))
            }
            tokens.add(YamlPatternToken.Array)
            continue
        }
        // Handle condition: [key=value] // TODO fix syntax to get using regexp
        if (segment.contains("[") && segment.endsWith("]") && segment.contains("=")) {
            val content = segment.substring(0, segment.length - 1)
            val parts = content.split("=")
            if (parts.size == 2) {
                tokens.add(YamlPatternToken.Key(segment.substringBefore("[")))
                tokens.add(YamlPatternToken.Array)
                val value = StringUtils.unwrap(parts[1], "'")
                tokens.add(YamlPatternToken.Condition(parts[0].substringAfter("["), value))
            }
            continue
        }
        // Handle value text match with parenthesis: key('value')
        val regex = Regex("""^([^(]+)\('([^']+)'\)$""")
        val match = regex.find(segment)
        if (match != null) {
            val key = match.groupValues[1]
            val valueText = match.groupValues[2]
            tokens.add(YamlPatternToken.Key(key))
            tokens.add(YamlPatternToken.ValueText(valueText))
            continue
        }
        // Otherwise, treat the segment as a plain key.
        tokens.add(YamlPatternToken.Key(segment))
    }
    return tokens
}

fun splitByDot(input: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()

    var inSingleQuote = false
    var inDoubleQuote = false
    var parenDepth = 0      // for parentheses: ( )
    var bracketDepth = 0    // for square brackets: [ ]

    for (c in input) {
        when (c) {
            '\'' -> {
                // Toggle single-quote state (only if not inside double quotes)
                if (!inDoubleQuote) {
                    inSingleQuote = !inSingleQuote
                }
                current.append(c)
            }
            '"' -> {
                // Toggle double-quote state (only if not inside single quotes)
                if (!inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote
                }
                current.append(c)
            }
            '(' -> {
                // Increase parenthesis depth only if not in quotes
                if (!inSingleQuote && !inDoubleQuote) {
                    parenDepth++
                }
                current.append(c)
            }
            ')' -> {
                if (!inSingleQuote && !inDoubleQuote && parenDepth > 0) {
                    parenDepth--
                }
                current.append(c)
            }
            '[' -> {
                if (!inSingleQuote && !inDoubleQuote) {
                    bracketDepth++
                }
                current.append(c)
            }
            ']' -> {
                if (!inSingleQuote && !inDoubleQuote && bracketDepth > 0) {
                    bracketDepth--
                }
                current.append(c)
            }
            '.' -> {
                // Only split if we're not inside quotes, parentheses, or brackets.
                if (!inSingleQuote && !inDoubleQuote && parenDepth == 0 && bracketDepth == 0) {
                    parts.add(current.toString())
                    current.clear()
                } else {
                    current.append(c)
                }
            }
            else -> {
                current.append(c)
            }
        }
    }
    if (current.isNotEmpty()) {
        parts.add(current.toString())
    }
    return parts
}
