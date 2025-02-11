package com.icthh.xm.xmeplugin.yaml

import com.icthh.xm.xmeplugin.extensions.LepMetadata
import com.icthh.xm.xmeplugin.utils.*
import com.icthh.xm.xmeplugin.yaml.YamlPath.YamlPathArray
import com.icthh.xm.xmeplugin.yaml.YamlPath.YamlPathKey
import com.icthh.xm.xmeplugin.yaml.YamlUtils.buildYamlTree
import com.icthh.xm.xmeplugin.yaml.YamlUtils.deepMerge
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import getTenantName
import org.jetbrains.yaml.psi.*


val Project.xmePluginSpecMetaInfoService get() = this.service<XmePluginSpecMetaInfoService>()

@Service(Service.Level.PROJECT)
class XmePluginSpecMetaInfoService(val project: Project) {

   fun getYamlContext(element: YAMLScalar, specKey: String): YamlContext? {
       val filesState = element.getFilesState(specKey)?.state
       val yamlNode = findYamlNodeForPsiElement(element, specKey) ?: return null
       return YamlContext(element, filesState, yamlNode, project)
   }


    private fun getPsiKeyPath(psi: YAMLPsiElement): List<YamlPath> {
        val keys = mutableListOf<YamlPath>()
        var current: PsiElement? = psi
        while (current != null) {
            when (current) {
                is YAMLKeyValue ->
                    keys.add(YamlPathKey(current.keyText))
                is YAMLSequenceItem ->
                    keys.add(YamlPathArray(current.itemIndex, psi.containingFile.virtualFile.path))
            }
            if (current is YAMLFile) break
            current = current.parent
        }
        return keys.reversed()
    }

    fun findYamlNodeForPsiElement(psiElement: YAMLScalar, specKey: String): YamlNode? {
        return psiElement.withCache("findYamlNodeForPsiElement-${specKey}") {
            val path: List<YamlPath> = getPsiKeyPath(psiElement)
            val root = psiElement.getYamlNode(specKey)
            var current: YamlNode? = root
            for (p in path) {
                current = current?.getChild(p)
                if (current == null) return@withCache null
            }
            return@withCache current
        }
    }

    fun YAMLScalar.getYamlNode(specKey: String): YamlNode? {
        val tenantName = containingFile?.getTenantName() ?: return null
        val files = this.project.xmePluginSpecService.getFiles(tenantName, specKey)
        val key = "xme-yaml-node-state-${specKey}-${tenantName}-${files.map { it.name }.joinToString("-")}"
        return this.withMultipleFilesCache(key, files) {
            buildYamlTree(this.getFilesState(specKey))
        }
    }

    val LAST_SPEC_STATE = Key.create<YamlFileValue>("LEP_EXPRESSION")
    private fun YAMLScalar.getFilesState(specKey: String): SpecState? {
        val tenantName = containingFile?.getTenantName() ?: return null
        val files = this.project.xmePluginSpecService.getFiles(tenantName, specKey)
        val key = "xme-spec-file-state-${tenantName}-${files.map { it.name }.joinToString("-")}"
        return this.withMultipleFilesCache(key, files) {
            log.info("Computing info for tenant $tenantName")
            files.mapNotNull {
                try {
                    val fileState = it.withCache("info-${tenantName}-${it.name}") {
                        YamlFileValue(it.virtualFile.path, mapOf("value" to it.readSpecYaml()))
                    }
                    it.putUserData(LAST_SPEC_STATE, fileState)
                } catch (e: Exception) {
                    log.warn("Error reading spec file ${it.name}", e)
                }
                it.getUserData(LAST_SPEC_STATE)
            }.joinSpecInfo()
        }
    }

    private fun List<YamlFileValue>.joinSpecInfo(): SpecState {
        val itemToFiles: MutableMap<Any, YamlPathArray> = mutableMapOf()
        val result = this.reduce { acc, any -> YamlFileValue(
            acc.filePath,
            deepMerge(acc.filePath, acc.value, any.filePath, any.value, itemToFiles)
        ) }
        return SpecState(result.value.get("value"), itemToFiles)
    }

    private fun PsiFile.readSpecYaml(): Any? {
        return YamlUtils.readYaml(this.text)
    }

    data class YamlFileValue(
        val filePath: String,
        val value: Map<*,*>
    )

    data class SpecState(
        val state: Any?,
        val itemToFiles: Map<Any, YamlPathArray>
    )


}
