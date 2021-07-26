package com.icthh.xm.extensions

import com.icthh.xm.service.isSupportProject
import com.icthh.xm.utils.logger
import com.icthh.xm.utils.psiElement
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.*
import com.intellij.psi.PsiReferenceRegistrar.HIGHER_PRIORITY
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl


class CommonsPsiReferenceContributor: PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(psiElement<PsiReferenceExpression>(), CommonsReferenceProvider(), HIGHER_PRIORITY)
    }
}

class CommonsReferenceProvider: PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element.text.contains("httpClarityService")) {
            logger.info("element ${element}")
        }
        return PsiReference.EMPTY_ARRAY
    }
}
