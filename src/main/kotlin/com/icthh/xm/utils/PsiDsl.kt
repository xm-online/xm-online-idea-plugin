package com.icthh.xm.utils

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.fileTypes.FileType
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.PsiElementPattern.Capture
import com.intellij.patterns.PsiFilePattern
import com.intellij.patterns.StandardPatterns
import com.intellij.patterns.StringPattern
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil.*
import com.intellij.psi.util.parents
import com.intellij.psi.util.parentsWithSelf
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.*

inline fun <reified T: PsiElement> psiElement(
    innerPattern: PsiDsl<T>.() -> Unit = { PsiDsl(psiElement(T::class.java)) }
): Capture<out T> {
    val capture = psiElement(T::class.java)
    val platformPatternsDsl = PsiDsl(capture) as PsiDsl<T>
    innerPattern.invoke(platformPatternsDsl)
    return platformPatternsDsl.toResult()
}

class PsiDsl<T: PsiElement>(var state: Capture<T>) {

    inline fun <reified M: PsiElement> withPsiParent(
        name: String? = null,
        noinline innerPattern: PsiDsl<M>.() -> Unit = {}
    ): PsiDsl<out PsiElement> {
        val dsl = PsiDsl(psiElement(M::class.java))
        if (name != null) {
            dsl.withName(name)
        }
        innerPattern.invoke(dsl)
        state = state.withParent(dsl.toResult())
        return dsl
    }

    fun withName(name: String) {
        state = state.withName(name)
    }

    fun inFile(
        innerPattern: FileDsl.() -> Unit = { FileDsl(psiFile()) }
    ) {
        val fileDsl = FileDsl(psiFile())
        innerPattern.invoke(fileDsl)
        state = state.inFile(fileDsl.toResult())
    }

    fun toResult() = state
}

fun PsiDsl<YAMLKeyValue>.toKeyValue(name: String? = null, innerPattern: PsiDsl<YAMLKeyValue>.() -> Unit = {}) {
    withPsiParent<YAMLMapping> {
        toSequenceKeyValue(name, innerPattern)
    }
}

fun PsiDsl<out YAMLPsiElement>.toSequenceKeyValue(
    name: String?,
    innerPattern: PsiDsl<YAMLKeyValue>.() -> Unit = {}
) {
    withPsiParent<YAMLSequenceItem> {
        withPsiParent<YAMLSequence> {
            withPsiParent<YAMLKeyValue> {
                if (name != null) {
                    withName(name)
                }
                innerPattern.invoke(this)
            }
        }
    }
}

fun PsiElement.findFirstParent(strict: Boolean = true, condition: (PsiElement?) -> Boolean): PsiElement? {
    return findFirstContext(this, strict, condition)
}

inline fun <reified T : PsiElement> PsiElement?.findChildOfType() = findChildOfType(this, T::class.java)
inline fun <reified T : PsiElement> PsiElement?.findChildrenOfType() = findChildrenOfType(this, T::class.java)

inline fun <reified T: PsiElement> PsiElement?.getParentOfType() = getParentOfType(this, T::class.java)
inline fun <reified T: PsiElement> PsiElement?.getChildOfType() = getChildOfType(this, T::class.java)
inline fun <reified T: PsiElement> PsiElement?.getChildrenOfType(): MutableList<T> = getChildrenOfTypeAsList(this, T::class.java)

class FileDsl(var state: PsiFilePattern.Capture<PsiFile>) {

    fun withOriginalFile(filePattern: FileDsl.() -> Unit) {
        val fileDsl = FileDsl(psiFile())
        filePattern.invoke(fileDsl)
        state = state.withOriginalFile(fileDsl.toResult())
    }

    fun withParentDirectoryName(pattern: StringPattern) {
        state = state.withParentDirectoryName(pattern)
    }

    fun withFileType(type: Class<out FileType>){
        state = state.withFileType(StandardPatterns.instanceOf(type))
    }

    fun or(innerPattern1: FileDsl.() -> Unit, innerPattern2: FileDsl.() -> Unit) {
        val dsl1 = FileDsl(state)
        innerPattern1.invoke(dsl1)
        val dsl2 = FileDsl(state)
        innerPattern2.invoke(dsl2)
        state = state.andOr(dsl1.toResult(), dsl2.toResult())
    }

    fun toResult() = state
}

fun PsiReferenceRegistrar.registerProvider(pattern: ElementPattern<out PsiElement?>,
                                           work: (scalar: PsiElement, context: ProcessingContext) -> Array<PsiReference>) {
    registerReferenceProvider(pattern, object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext) = work.invoke(element, context)
    })
}

fun PsiElement.getChildrenByPath(vararg types: Class<out PsiElement>): List<PsiElement> {
    var result: List<PsiElement> = listOf(this)
    types.forEach { type ->
        result = result.flatMap { getChildrenOfTypeAsList(it, type) }
    }
    return result
}

fun CompletionContributor.extendWithStop(type: CompletionType, place: ElementPattern<out PsiElement>,
                                         provider: (parameters: CompletionParameters) -> List<LookupElement>) {
    this.extend(type, place, object: CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            result.addAllElements(provider(parameters))
            result.stopHere()
        }
    })
}

fun YAMLKeyValue?.keyTextMatches(key: String): Boolean {
    return this?.key?.textMatches(key) ?: false
}

fun YAMLSequence?.getKeys(): List<YAMLKeyValue> {
    return mapToFields("key")
}

fun YAMLSequence?.mapToFields(fieldName: String): List<YAMLKeyValue> {
    val yamlSequence = this
    return yamlSequence.getChildrenOfType<YAMLSequenceItem>()
        .map { it.getChildOfType<YAMLMapping>() }
        .map { it.getChildrenOfType<YAMLKeyValue>() }
        .flatten()
        .filter { it.keyTextMatches(fieldName) }
}

