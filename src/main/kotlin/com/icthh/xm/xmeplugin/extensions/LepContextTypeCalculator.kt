package com.icthh.xm.xmeplugin.extensions

import com.icthh.xm.xmeplugin.services.TENANT_CONFIG_FIELD
import com.icthh.xm.xmeplugin.services.TENANT_CONFIG_FIELD_PATH
import com.icthh.xm.xmeplugin.utils.isSupportProject
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiField
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.createDescriptor
import org.jetbrains.plugins.groovy.lang.typing.DefaultMethodReferenceTypeCalculator
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator

val LEP_EXPRESSION = Key.create<LepMetadata>("LEP_EXPRESSION")

data class LepMetadata(val lepContextPath: String) {
    constructor(lepExpression: LepMetadata, name: String):
            this("${lepExpression.lepContextPath}.${name}")
}

class LepContextTypeCalculator : GrTypeCalculator<GrReferenceExpression> {

    val delegate = DefaultMethodReferenceTypeCalculator()

    override fun getType(expression: GrReferenceExpression): PsiType? {
        if (!expression.project.isSupportProject()) return delegate.getType(expression)

        val fieldName = expression.createDescriptor()?.getName()
        val qualifier = expression.qualifier

        if (fieldName == "lepContext" && qualifier == null) {
            expression.putUserData(LEP_EXPRESSION, LepMetadata(fieldName))
        }

        val lepExpression = qualifier?.getUserData(LEP_EXPRESSION)
        if (lepExpression != null && fieldName != null) {
            expression.putUserData(LEP_EXPRESSION, LepMetadata(lepExpression, fieldName))
        }

        val field = expression.rValueReference?.resolve()
        if (field?.getUserData(TENANT_CONFIG_FIELD) == true && field is PsiField) {
            val referenceNameElement = expression.referenceNameElement
            referenceNameElement?.putUserData(TENANT_CONFIG_FIELD, field.getUserData(TENANT_CONFIG_FIELD))
            referenceNameElement?.putUserData(TENANT_CONFIG_FIELD_PATH, field.getUserData(TENANT_CONFIG_FIELD_PATH))
        }

        return delegate.getType(expression)
    }

}
