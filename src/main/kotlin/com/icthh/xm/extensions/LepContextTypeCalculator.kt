package com.icthh.xm.extensions

import com.icthh.xm.utils.logger
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
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
        val fieldName = expression.createDescriptor()?.getName()
        val qualifier = expression.qualifier

        if (fieldName == "lepContext" && qualifier == null) {
            expression.putUserData(LEP_EXPRESSION, LepMetadata(fieldName))
        }

        val lepExpression = qualifier?.getUserData(LEP_EXPRESSION)
        if (lepExpression != null && fieldName != null) {
            expression.putUserData(LEP_EXPRESSION, LepMetadata(lepExpression, fieldName))
        }

        return delegate.getType(expression)
    }

}
