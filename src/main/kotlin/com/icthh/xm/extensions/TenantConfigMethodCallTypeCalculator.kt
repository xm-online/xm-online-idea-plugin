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
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.createDescriptor
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.sorryCannotKnowElementKind
import org.jetbrains.plugins.groovy.lang.typing.DefaultMethodCallTypeCalculator
import org.jetbrains.plugins.groovy.lang.typing.DefaultMethodReferenceTypeCalculator
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator

val TENANT_CONFIG_EXPRESSION = "lepContext.services.tenantConfigService"

class TenantConfigMethodCallTypeCalculator : GrTypeCalculator<GrMethodCall> {

    val TENANT_CONFIG = "com.icthh.xm.commons.config.client.service.TenantConfigService"
    val GET_CONFIG = "getConfig"
    val delegate = DefaultMethodCallTypeCalculator()

    override fun getType(expression: GrMethodCall): PsiType? {
        if (isTenantConfig(expression)) {
            val virtualFile = expression.originalFile.virtualFile ?: return delegate.getType(expression)
            val project = expression.project
            val type = project.getTenantConfigService().getPsiTypeForExpression(virtualFile, project, expression)
            return type ?: delegate.getType(expression)
        }

        return delegate.getType(expression)
    }

    private fun isTenantConfig(expression: GrMethodCall): Boolean {
        val methodName = GET_CONFIG.equals(expression.callReference?.methodName)
        val isTenantConfigType = TENANT_CONFIG.equals(expression.resolveMethod()?.containingClass?.qualifiedName)

        val invokedExpression = expression.invokedExpression as? GrReferenceExpression
        val isTenantConfigLepExpression = invokedExpression?.getQualifierExpression()?.getUserData(LEP_EXPRESSION)?.lepContextPath == TENANT_CONFIG_EXPRESSION

        return methodName && (isTenantConfigType || isTenantConfigLepExpression)
    }

}

class TenantConfigPropertyTypeCalculator : GrTypeCalculator<GrReferenceExpression> {

    val TENANT_CONFIG = "com.icthh.xm.commons.config.client.service.TenantConfigService"
    val GET_CONFIG = "config"
    val delegate = DefaultMethodReferenceTypeCalculator()

    override fun getType(expression: GrReferenceExpression): PsiType? {
        val project = expression.project
        val fieldName = expression.createDescriptor()?.getName()
        if (fieldName == GET_CONFIG) {
            val tenantConfigService = project.getTenantConfigService()
            if (isTenantConfig(expression)) {
                val virtualFile = expression.originalFile.virtualFile ?: return delegate.getType(expression)
                val psiTypeForExpression = tenantConfigService.getPsiTypeForExpression(virtualFile, project, expression)
                return psiTypeForExpression ?: delegate.getType(expression)
            }
        }

        return delegate.getType(expression)
    }

    private fun isTenantConfig(expression: GrReferenceExpression): Boolean {
        val qualifier = expression.qualifier
        val psiType = qualifier?.type
        val isTenantConfigExpression = qualifier?.getUserData(LEP_EXPRESSION)?.lepContextPath == TENANT_CONFIG_EXPRESSION
        val isTenantType = psiType is PsiClassReferenceType && psiType.reference.qualifiedName == TENANT_CONFIG
        return isTenantConfigExpression || isTenantType
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
        val type = if (reference is GrReferenceExpression) {
            getType(reference) ?: return
        } else if (reference is GrMethodCall) {
            getType(reference) ?: return
        } else {
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

    private fun getType(methodCall: GrMethodCall): PsiClassType? {
        return methodCall.nominalType as? PsiClassType
    }

    private fun getType(reference: GrReferenceExpression): PsiClassType? {
        val referenceDescriptor = reference.createDescriptor() as? ResolvedVariableDescriptor
        val type = referenceDescriptor?.variable?.typeGroovy
        if (type is PsiClassType) {
            return type
        }

        val elementType = (reference.element as? GrReferenceExpressionImpl)?.type
        if (elementType is PsiClassType) {
            return elementType
        }

        val field = reference.resolve() as? PsiField
        return field?.type as? PsiClassType
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
