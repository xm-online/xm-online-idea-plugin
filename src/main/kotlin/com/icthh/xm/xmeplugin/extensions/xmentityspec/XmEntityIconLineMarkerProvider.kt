package com.icthh.xm.xmeplugin.extensions.xmentityspec

import com.icthh.xm.xmeplugin.utils.FontIcon
import com.icthh.xm.xmeplugin.utils.keyTextMatches
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.CENTER
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.yaml.psi.YAMLKeyValue
import readTextAndClose
import java.awt.Font
import java.awt.GraphicsEnvironment


class XmEntityIconLineMarkerProvider: LineMarkerProviderDescriptor() {

    override fun getName(): String = "Icon marker"

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<LeafPsiElement>? {
        val result = doWork(psiElement)
        return result
    }

    private fun doWork(psiElement: PsiElement): LineMarkerInfo<LeafPsiElement>? {
        if (psiElement !is LeafPsiElement || !psiElement.isEntitySpecification()) {
            return null
        }

        val element = psiElement.parent?.parent ?: return null
        if (element is YAMLKeyValue && element.keyTextMatches("icon")) {
            val iconName = element.valueText
            val icon = IconProvider.iconsSet[iconName] ?: return null
            return LineMarkerInfo(psiElement, psiElement.getTextRange(), icon, null, null, CENTER) {
                iconName
            }
        }

        return null;
    }

}

object IconProvider {
    val font: Font = loadFont()
    val iconsSet: Map<String, FontIcon> = localIconsSet()

    fun loadFont() =
        javaClass.classLoader.getResourceAsStream("icons/mdi/MaterialIcons-Regular.ttf").use {
            var font = Font.createFont(Font.TRUETYPE_FONT, it)
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            font = font.deriveFont(14f)
            font
        }

    fun localIconsSet() =
        javaClass.classLoader.getResourceAsStream("icons/mdi/codepoints.txt").use {
            it.readTextAndClose().lines().map { it.split(" ") }
                .filter { it.size == 2 }
                .map { it[0] to it[1] }
                .map {
                    it.first to Integer.parseInt(it.second, 16).toChar()
                }.map {
                    it.first to FontIcon(font, it.second)
                }.toMap()
        }
}
