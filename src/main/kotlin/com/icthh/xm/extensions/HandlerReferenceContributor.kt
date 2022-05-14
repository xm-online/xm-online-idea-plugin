package com.icthh.xm.extensions

import com.icthh.xm.extensions.entityspec.*
import com.icthh.xm.service.toPsiFile
import com.icthh.xm.utils.childrenOfType
import com.icthh.xm.utils.keyTextMatches
import com.icthh.xm.utils.psiElement
import com.icthh.xm.utils.registerProvider
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.*

class HandlerReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {

        registrar.registerProvider(schedulerTypeKey("typeKey")) { element, _ ->
            schedulerReference(element)
        }
        registrar.registerProvider(schedulerTypeKey("key")) { element, _ ->
            schedulerReference(element)
        }
    }

    private fun schedulerReference(element: PsiElement): Array<PsiReference> =
        element.withCache {

            val typeKey = element.parentOfType<YAMLMapping>()?.childrenOfType<YAMLKeyValue>()?.filter {
                it.keyTextMatches("typeKey")
            }?.first()?.valueText ?: return@withCache PsiReference.EMPTY_ARRAY
            val targetMs = element.parentOfType<YAMLMapping>()?.childrenOfType<YAMLKeyValue>()?.filter {
                it.keyTextMatches("targetMs")
            }?.first()?.valueText ?: return@withCache PsiReference.EMPTY_ARRAY

            val file = element.originalFile.virtualFile.parent.parent
                .findChild(targetMs)
                ?.findFileByRelativePath("lep/scheduler/SchedulerEvent\$\$${translateToLepConvention(typeKey)}\$\$around.groovy")
                ?.toPsiFile(element.project)

            file ?: return@withCache PsiReference.EMPTY_ARRAY
            Array(1) {
                PsiReferenceImpl(element, file)
            }

        }

    private fun schedulerTypeKey(field: String) = psiElement<YAMLScalar>()
        .inFile(filePattern())
        .withParent(
            psiElement<YAMLKeyValue>().withName(field).withParent(
                psiElement<YAMLMapping>().withParent(
                    psiElement<YAMLSequenceItem>().withParent(
                        psiElement<YAMLSequence>().withParent(
                            psiElement<YAMLKeyValue>().withName("tasks")
                        )
                    )
                )
            )
        )


    private fun filePattern() = psiFile().withName("tasks.yml").with(object : PatternCondition<PsiFile>("path") {
        override fun accepts(t: PsiFile, context: ProcessingContext?): Boolean {
                return t.virtualFile.path.contains("/scheduler/tasks.yml")
            }
        })

}
