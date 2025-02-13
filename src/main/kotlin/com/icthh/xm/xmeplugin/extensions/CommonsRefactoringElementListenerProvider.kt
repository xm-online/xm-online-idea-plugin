package com.icthh.xm.xmeplugin.extensions

import com.icthh.xm.xmeplugin.utils.isTrue
import com.icthh.xm.xmeplugin.utils.runWriteAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider


class CommonsRefactoringElementListenerProvider : RefactoringElementListenerProvider {

    override fun getListener(element: PsiElement): RefactoringElementListener? {
        if (element is PsiMethod && element.getUserData(COMMONS_METHOD_WITH_SIGNATURE).isTrue) {
            return CommonsMethodRefactoringElementListener(element)
        }
        return null
    }
}

class CommonsMethodRefactoringElementListener(private val originalMethod: PsiMethod) : RefactoringElementListener {
    override fun elementRenamed(newElement: PsiElement) {
        if (newElement is PsiMethod && (originalMethod.getUserData(COMMONS_METHOD_WITH_SIGNATURE).isTrue)) {
            originalMethod.getUserData(COMMONS_METHOD_FILE)?.let { file ->
                val newFileName = newElement.name
                val virtualFile = file.virtualFile ?: return
                runWriteAction {
                    val suffix = if (virtualFile.name.endsWith("around.groovy")) {
                        "\$\$around.groovy"
                    } else {
                        ".groovy"
                    }
                    val newName = "Commons\$\$${newFileName}${suffix}"
                    virtualFile.rename(this, newName)
                }
            }
        }
    }

    override fun elementMoved(newElement: PsiElement) {
        // Optionally handle the case where the element is moved.
    }

    private fun renameConfigFile(oldName: String, newName: String) {
        println(oldName);
        println(newName);
    }
}
