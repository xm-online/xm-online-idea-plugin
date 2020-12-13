package com.icthh.xm.extensions.entityspec

import com.icthh.xm.utils.isTrue
import com.icthh.xm.utils.start
import com.icthh.xm.utils.stop
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLKeyValue
import java.awt.Color

class XmEntitySpecElementColorProvider: ElementColorProvider {
    override fun setColorTo(psiElement: PsiElement, color: Color) {
        if (psiElement !is LeafPsiElement) {
            return
        }

        val element = psiElement.parent?.parent ?: return
        if (element is YAMLKeyValue && element.key?.textMatches("color").isTrue() && psiElement.isEntitySpecification()) {
            val elementGenerator = YAMLElementGenerator.getInstance(element.project)
            val hex = "#" + Integer.toHexString(color.getRGB()).substring(2)
            val colorKeyValue = elementGenerator.createYamlKeyValue("color", "\"${hex}\"")
            val value = colorKeyValue.value ?: return
            element.setValue(value)
            element.project.save()
            (element.parent.node as CompositeElement).subtreeChanged()
        }

    }

    override fun getColorFrom(psiElement: PsiElement): Color? {
        start("getColorFrom")
        val result = doWork(psiElement)
        stop("getColorFrom")
        return result
    }

    private fun doWork(psiElement: PsiElement): Color? {
        if (psiElement !is LeafPsiElement) {
            return null
        }

        val element = psiElement.parent?.parent ?: return null

        if (element is YAMLKeyValue && element.keyText.equals("color") && psiElement.isEntitySpecification()) {
            try {
                return Color.decode(element.valueText.toUpperCase())
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

}
