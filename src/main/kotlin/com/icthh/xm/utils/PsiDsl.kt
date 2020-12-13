package com.icthh.xm.utils

import com.intellij.openapi.fileTypes.FileType
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.PsiElementPattern.Capture
import com.intellij.patterns.PsiFilePattern
import com.intellij.patterns.StandardPatterns
import com.intellij.patterns.StringPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.getChildOfType
import com.intellij.psi.util.PsiTreeUtil.getChildrenOfTypeAsList
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem

inline fun <reified T: PsiElement> psiElement(
    innerPattern: PsiDsl<T>.() -> Unit = { PsiDsl(psiElement(T::class.java)) }
): Capture<out T> {
    val capture = psiElement(T::class.java)
    val platformPatternsDsl = PsiDsl(capture)
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
}

fun PsiElement.findFirstParent(strict: Boolean = true, condition: (PsiElement?) -> Boolean): PsiElement? {
    return PsiTreeUtil.findFirstContext(this, strict, condition)
}

inline fun <reified T: PsiElement> PsiElement?.getChildOfType() = getChildOfType(this, T::class.java)
inline fun <reified T: PsiElement> PsiElement?.getChildrenOfType() = getChildrenOfTypeAsList(this, T::class.java)

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

