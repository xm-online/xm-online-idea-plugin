package com.icthh.xm.extensions

import com.icthh.xm.extensions.entityspec.originalFile
import com.icthh.xm.service.*
import com.icthh.xm.utils.getCountSubstring
import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.logger
import com.icthh.xm.utils.psiElement
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ElementPatternCondition
import com.intellij.patterns.InitialPatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.PsiReferenceRegistrar.HIGHER_PRIORITY
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.createDescriptor
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
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
            val virtualFile = place.originalFile.virtualFile ?: return
            val tenantName = virtualFile.getTenantName(project)
            val fields = tenantConfigService.getFields(tenantName.uppercase(), aClass.name ?: "")
            fields.forEach {
                inject(
                    aClass,
                    psiManager,
                    "'${it.name}'",
                    it.psiType,
                    processor,
                    state,
                    it.navigationElement
                )
            }
        }

    }

    private fun inject(
        aClass: PsiClass,
        psiManager: PsiManager,
        name: String,
        type: PsiType,
        processor: PsiScopeProcessor,
        state: ResolveState,
        navigationElement: PsiElement
    ) {
        //val param = LightFieldBuilder(psiManager, name, TenantConfigPsiType(type as PsiClassReferenceType))
        val param = GrLightField(aClass, name, TenantConfigPsiType(type as PsiClassReferenceType), navigationElement)
        val newState = state.put(sorryCannotKnowElementKind, true)
        processor.execute(param, newState)
    }

    private fun isTenantConfig(aClass: PsiClass): Boolean {
        return aClass.name?.endsWith(TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME).isTrue()
    }
}

open class HideInternalFieldsCompletionContributor: CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        processInternalFields(parameters) {
            it.navigationElement.modifierList?.setModifierProperty("public", false)
            it.navigationElement.modifierList?.setModifierProperty("private", true)
        }
    }

    protected fun processInternalFields(
        parameters: CompletionParameters,
        action: (TenantConfigService.FieldHolder) -> Unit
    ) {
        val reference = parameters.position.prevSibling?.prevSibling
        if (reference !is GrReferenceExpression) {
            return
        }

        val referenceDescriptor = reference.createDescriptor()
        if (referenceDescriptor !is ResolvedVariableDescriptor) {
            return
        }

        val type = referenceDescriptor.variable.typeGroovy
        if (type !is PsiClassType) {
            return
        }

        if (!type.name.endsWith(TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME).isTrue()) {
            return
        }

        val project = parameters.position.project ?: return
        val tenantConfigService = project.getTenantConfigService()
        val virtualFile = parameters.originalFile.virtualFile ?: return
        val tenantName = virtualFile.getTenantName(project)
        val fields = tenantConfigService.getFields(tenantName.uppercase(), type.name ?: "")

        fields.forEach(action)
    }

}

class UnhideInternalFieldsCompletionContributor: HideInternalFieldsCompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        processInternalFields(parameters) {
            it.navigationElement.modifierList?.setModifierProperty("private", false)
            it.navigationElement.modifierList?.setModifierProperty("public", true)
        }
    }

}
