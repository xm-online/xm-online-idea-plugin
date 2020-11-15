package com.icthh.xm.extensions.entityspec

import com.icthh.xm.extensions.entityspec.IconProvider.iconsSet
import com.icthh.xm.utils.FontIcon
import com.icthh.xm.utils.readTextAndClose
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataConstants
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.CENTER
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.apache.commons.lang3.time.StopWatch
import org.jetbrains.yaml.psi.YAMLKeyValue
import java.awt.Font
import java.awt.GraphicsEnvironment


class XmEntityIconLineMarkerProvider: LineMarkerProviderDescriptor() {

    override fun getName(): String = "Icon marker"

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<LeafPsiElement>? {
        val timer = StopWatch.createStarted()

        if (psiElement !is LeafPsiElement || !psiElement.isEntitySpecification()) {
            return null
        }

        val element = psiElement.parent?.parent ?: return null
        if (element is YAMLKeyValue && element.keyText.equals("icon")) {
            val iconName = element.valueText
            val icon = iconsSet[iconName] ?: return null

            val lineMarkerInfo = LineMarkerInfo(psiElement, psiElement.getTextRange(), icon, null, null, CENTER)
            return lineMarkerInfo
        }

        return null;
    }

}

object IconProvider {
    val font: Font = loadFont()
    val iconsSet: Map<String, FontIcon> = localIconsSet()

    fun loadFont() =
        javaClass.classLoader.getResourceAsStream("/icons/mdi/MaterialIcons-Regular.ttf").use {
            var font = Font.createFont(Font.TRUETYPE_FONT, it)
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            font = font.deriveFont(14f)
            font
        }

    fun localIconsSet() =
        javaClass.classLoader.getResourceAsStream("/icons/mdi/codepoints.txt").use {
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