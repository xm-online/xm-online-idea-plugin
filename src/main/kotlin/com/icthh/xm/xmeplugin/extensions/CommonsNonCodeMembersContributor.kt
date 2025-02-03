package com.icthh.xm.xmeplugin.extensions

import com.icthh.xm.xmeplugin.utils.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.sorryCannotKnowElementKind

class CommonsNonCodeMembersContributor: NonCodeMembersContributor() {

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
        if (!project.isSupportProject() || project.isConfigProject()) {
            return
        }

        val parent = place.parent
        val commons = "lepContext.commons"

        if (!(aClass != null && aClass.getCountSubstring() > 0)) {
            return
        }


        if (parent != null && place.text.startsWith(commons)) {
            val lepCommons = getLepFolder(project, place)?.children?.find { it.name == "commons" } ?: return

            val nameHint = processor.getHint(NameHint.KEY)?.getName(state)
            if (nameHint != null && place.text.endsWith("." + nameHint)) {
                var path = place.text.substringAfter(commons + ".")
                path = path.substringBefore("(")
                var pathParts = path.split(".")
                var targetFolder: VirtualFile? = lepCommons
                val DOLLARS = "$" + "$"
                pathParts.forEach { pathPart ->
                    targetFolder = targetFolder?.children?.find {
                        it.name == pathPart || it.name == "Commons${DOLLARS}${pathPart}${DOLLARS}around.groovy" || it.name == "Commons${DOLLARS}${pathPart}.groovy"
                    }
                }

                val folder = targetFolder
                if (folder != null) {
                    val psiFile = folder.toPsiElement(project) ?: return
                    inject(nameHint, psiFile, processor, state)
                }
            }
        }
    }

    private fun inject(
        name: String,
        element: PsiFileSystemItem,
        processor: PsiScopeProcessor,
        state: ResolveState
    ) {
            val param = LightFieldBuilder(name, "java.lang.Object", element)
            val newState = state.put(sorryCannotKnowElementKind, true)
            processor.execute(param, newState)
    }

}
