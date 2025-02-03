package com.icthh.xm.xmeplugin.extensions.xmentityspec

import com.icthh.xm.xmeplugin.utils.doAsync
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ui.UIUtil
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLKeyValue
import java.awt.Color
import java.lang.Boolean.TRUE

class XmEntitySpecElementColorProvider: ElementColorProvider {

    override fun setColorTo(psiElement: PsiElement, color: Color) {
        if (psiElement !is LeafPsiElement) return

        val keyValue = psiElement.parent?.parent as? YAMLKeyValue ?: return
        if (!keyValue.keyText.equals("color", ignoreCase = true) || !psiElement.isEntitySpecification()) {
            return
        }

        val hexColor = "#%02X%02X%02X".format(color.red, color.green, color.blue)
        WriteCommandAction.runWriteCommandAction(keyValue.project) {
            val elementGenerator = YAMLElementGenerator.getInstance(keyValue.project)
            val colorKeyValue = elementGenerator.createYamlKeyValue("color", "\"$hexColor\"")
            val newValue = colorKeyValue.value ?: return@runWriteCommandAction
            keyValue.setValue(newValue)
        }
    }

    override fun getColorFrom(psiElement: PsiElement): Color? {
        if (psiElement !is LeafPsiElement) return null
        val keyValue = psiElement.parent?.parent as? YAMLKeyValue ?: return null
        if (!keyValue.keyText.equals("color", ignoreCase = true) || !psiElement.isEntitySpecification()) {
            return null
        }

        return try {
            Color.decode(keyValue.valueText.uppercase())
        } catch (e: Exception) {
            null
        }
    }

}
