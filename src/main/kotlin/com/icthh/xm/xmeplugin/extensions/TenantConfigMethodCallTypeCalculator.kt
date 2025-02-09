package com.icthh.xm.xmeplugin.extensions

import com.icthh.xm.xmeplugin.services.*
import com.icthh.xm.xmeplugin.utils.*
import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.ProcessingContext
import getTenantName
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


const val TENANT_CONFIG_EXPRESSION = "lepContext.services.tenantConfigService"

class TenantConfigMethodCallTypeCalculator : GrTypeCalculator<GrMethodCall> {

    val TENANT_CONFIG = "com.icthh.xm.commons.config.client.service.TenantConfigService"
    val GET_CONFIG = "getConfig"
    val delegate = DefaultMethodCallTypeCalculator()

    override fun getType(expression: GrMethodCall): PsiType? {
        if (!expression.project.isSupportProject()) return delegate.getType(expression)
        if (isTenantConfig(expression)) {
            val virtualFile = expression.originalFile.virtualFile ?: return delegate.getType(expression)
            val project = expression.project
            val type = project.tenantConfigService.getPsiTypeForExpression(virtualFile, project, expression)
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
        if (!project.isSupportProject()) return delegate.getType(expression)

        val fieldName = expression.createDescriptor()?.getName()
        val text = expression.text
        if (text == "lepContext.services.tenantConfigService") {
            return expression.project.tenantConfigService.getTenantConfigServiceType(expression.project)
        }
        if (fieldName == GET_CONFIG) {
            val tenantConfigService = project.tenantConfigService
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
        val isMockTenantType = psiType is PsiImmediateClassType && psiType.resolve()?.qualifiedName == TENANT_CONFIG
        return isTenantConfigExpression || isTenantType || isMockTenantType
    }

}

// TODO add navigate to property in tenant config

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
        if (!project.isSupportProject()) return
        val selected = project.getSettings().selected()
        selected ?: return

        if (!(aClass != null && aClass.getCountSubstring() > 0)) {
            return
        }

        if (isTenantConfig(aClass)) {
            val tenantConfigService = project.tenantConfigService
            val virtualFile = place.originalFile.virtualFile.toPsiFile(project) ?: return
            val tenantName = virtualFile.getTenantName()
            val fields = tenantConfigService.getFields(tenantName.uppercase(), aClass.name ?: "")
            fields.forEach { field ->
                val param = GrLightField(
                    aClass,
                    "'${field.name}'",
                    TenantConfigPsiType(field.psiType as PsiClassReferenceType), field.navigationElement
                )
                param.putUserData(TENANT_CONFIG_FIELD, true)
                var userData = field.navigationElement.getUserData(TENANT_CONFIG_FIELD_PATH)
                userData = userData?.map { it.replace(field.technicalName, field.name) }?.toMutableList()
                param.putUserData(TENANT_CONFIG_FIELD_PATH, userData)
                val newState = state.put(sorryCannotKnowElementKind, true)
                processor.execute(param, newState)
            }
        }

    }

    private fun isTenantConfig(aClass: PsiClass): Boolean {
        return aClass.name?.endsWith(TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME).isTrue
    }
}

class HideTenantConfigFieldsCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().with(object : PatternCondition<PsiElement>("is tenant config") {
                override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
                    return t.project.isSupportProject() && isTenantConfigField(t)
                }
            }),
            object : CompletionProvider<CompletionParameters?>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    resultSet: CompletionResultSet
                ) {

                    process(parameters) { fields ->
                        val internalFields = fields.map { it.name }.toMutableSet()
                        resultSet.runRemainingContributors(parameters) { completionResult: CompletionResult ->
                            val element = completionResult.lookupElement
                            val psiElement = element.psiElement
                            if (psiElement is PsiField && internalFields.contains(psiElement.name)) {
                                return@runRemainingContributors
                            }
                            resultSet.addElement(element)
                        }
                    }
                }
            })
    }

    fun process(parameters: CompletionParameters, action: (List<TenantConfigService.FieldHolder>) -> Unit) {
        val reference = parameters.position.prevSibling?.prevSibling
        val type = if (reference is GrReferenceExpression) {
            getType(reference) ?: return
        } else if (reference is GrMethodCall) {
            getType(reference) ?: return
        } else {
            return
        }

        if (!type.name.endsWith(TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME).isTrue) {
            return
        }

        val project = parameters.position.project
        val tenantConfigService = project.tenantConfigService
        val virtualFile = parameters.originalFile.virtualFile ?: return
        val tenantName = virtualFile.getTenantName(project)
        val fields = tenantConfigService.getFields(tenantName.uppercase(), type.name)
        action(fields)
    }
}


fun isTenantConfigField(t: PsiElement): Boolean {
    val reference = t.prevSibling?.prevSibling
    if (t.getUserData(TENANT_CONFIG_FIELD).isTrue) {
        return true
    }
    val type = when (reference) {
        is GrReferenceExpression -> getType(reference) ?: return false
        is GrMethodCall -> getType(reference) ?: return false
        else -> return false
    }
    return type.name.endsWith(TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME).isTrue
}

fun getType(methodCall: GrMethodCall): PsiClassType? {
    return methodCall.nominalType as? PsiClassType
}


fun getType(reference: GrReferenceExpression): PsiClassType? {
    return reference.withCache(reference.qualifierExpression?.text ?: "gr-ref-exp") {
        val referenceDescriptor = reference.createDescriptor() as? ResolvedVariableDescriptor
        val type = referenceDescriptor?.variable?.typeGroovy
        if (type is PsiClassType) {
            return@withCache type
        }

        val elementType = (reference.element as? GrReferenceExpressionImpl)?.type
        if (elementType is PsiClassType) {
            return@withCache elementType
        }

        val field = reference.resolve() as? PsiField
        return@withCache field?.type as? PsiClassType
    }
}
