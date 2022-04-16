package com.icthh.xm.extensions

import com.icthh.xm.extensions.entityspec.originalFile
import com.icthh.xm.service.*
import com.icthh.xm.utils.getCountSubstring
import com.icthh.xm.utils.isTrue
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.searches.AnnotatedMembersSearch
import com.intellij.psi.util.ClassUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.createDescriptor
import org.jetbrains.plugins.groovy.lang.psi.impl.stringValue
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.sorryCannotKnowElementKind
import org.jetbrains.plugins.groovy.lang.typing.DefaultMethodCallTypeCalculator
import org.jetbrains.plugins.groovy.lang.typing.DefaultMethodReferenceTypeCalculator
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator


class TenantConfigMethodCallTypeCalculator : GrTypeCalculator<GrMethodCall> {

    val TENANT_CONFIG = "com.icthh.xm.commons.config.client.service.TenantConfigService"
    val GET_CONFIG = "getConfig"
    val delegate = DefaultMethodCallTypeCalculator()

    override fun getType(expression: GrMethodCall): PsiType? {
        val project = expression.project
        if (project.isConfigProject()) {
            return delegate.getType(expression)
        }

        if (
            TENANT_CONFIG.equals(expression.resolveMethod()?.containingClass?.qualifiedName)
            &&
            GET_CONFIG.equals(expression.callReference?.methodName)
        ) {
            val virtualFile = expression.originalFile.virtualFile ?: return delegate.getType(expression)
            val project = expression.project
            val type = project.getTenantConfigService().getPsiTypeForExpression(virtualFile, project, expression)
            return type ?: delegate.getType(expression)
        }

        return delegate.getType(expression)
    }

}

class TenantConfigPropertyTypeCalculator : GrTypeCalculator<GrReferenceExpression> {

    val TENANT_CONFIG = "com.icthh.xm.commons.config.client.service.TenantConfigService"
    val GET_CONFIG = "config"
    val delegate = DefaultMethodReferenceTypeCalculator()

    override fun getType(expression: GrReferenceExpression): PsiType? {
        val project = expression.project
        if (project.isConfigProject()) {
            return delegate.getType(expression)
        }

        val fieldName = expression.createDescriptor()?.getName()
        if (fieldName == GET_CONFIG) {
            val tenantConfigService = project.getTenantConfigService()
            val psiType = expression.qualifier?.type
            if (psiType is PsiClassReferenceType && psiType.reference.qualifiedName == TENANT_CONFIG) {
                val virtualFile = expression.originalFile.virtualFile ?: return delegate.getType(expression)
                val project = expression.project
                val psiTypeForExpression = tenantConfigService.getPsiTypeForExpression(virtualFile, project, expression)
                return psiTypeForExpression ?: delegate.getType(expression)
            }
        }

        return delegate.getType(expression)
    }

}

class TenantConfigPsiType(val type: PsiClassReferenceType): PsiClassReferenceType(type.reference, null) {
    override fun isValid(): Boolean {
        return true
    }

}

class TenantConfigWrongNameFields: NonCodeMembersContributor() {
    override fun processDynamicElements(
        qualifierType: PsiType,
        aClass: PsiClass?,
        processor: PsiScopeProcessor,
        place: PsiElement,
        state: ResolveState
    ) {
        val project = place.project
        val selected = project.getSettings().selected()
        selected ?: return
        if (project.isConfigProject()) {
            return
        }

        if (!(aClass != null && aClass.getCountSubstring() > 0)) {
            return
        }

        if (isTenantConfig(aClass)) {
            val psiManager = PsiManager.getInstance(project)
            val tenantConfigService = project.getTenantConfigService()
            val virtualFile = place.originalFile.virtualFile
            val tenantName = virtualFile.getTenantName(project)
            val fields = tenantConfigService.getFields(tenantName.toUpperCase(), aClass.name ?: "")
            fields.forEach {
                inject(psiManager, "'${it.name}'", it.psiType, processor, state, it.navigationElement)
            }
        }

    }

    private fun inject(
        psiManager: PsiManager,
        name: String,
        type: PsiType,
        processor: PsiScopeProcessor,
        state: ResolveState,
        navigationElement: PsiElement
    ) {
        val param = LightFieldBuilder(psiManager, name, TenantConfigPsiType(type as PsiClassReferenceType))
        // val param = LightFieldBuilder(name, TenantConfigPsiType(type as PsiClassReferenceType), navigationElement)
        val newState = state.put(sorryCannotKnowElementKind, true)
        processor.execute(param, newState)
    }

    private fun isTenantConfig(aClass: PsiClass): Boolean {
        return aClass.name?.endsWith(TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME).isTrue()
    }
}
