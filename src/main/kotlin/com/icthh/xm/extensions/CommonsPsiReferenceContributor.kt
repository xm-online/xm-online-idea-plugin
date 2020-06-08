package com.icthh.xm.extensions

import com.icthh.xm.service.isSupportProject
import com.icthh.xm.utils.logger
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.*
import com.intellij.psi.PsiReferenceRegistrar.HIGHER_PRIORITY
import com.intellij.util.ProcessingContext


class CommonsPsiReferenceContributor: PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(StandardPatterns.instanceOf(PsiElement::class.java), CommonsReferenceProvider(), HIGHER_PRIORITY)
    }
}



class CommonsReferenceProvider: PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        element.project.isSupportProject() ?: return PsiReference.EMPTY_ARRAY
        logger.info("${element.text}")
        return PsiReference.EMPTY_ARRAY
    }
}