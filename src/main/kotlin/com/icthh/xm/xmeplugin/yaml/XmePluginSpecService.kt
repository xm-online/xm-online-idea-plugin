package com.icthh.xm.xmeplugin.yaml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.icthh.xm.xmeplugin.utils.*
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.FileContentUtil
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import getTenantName
import toVirtualFile
import java.io.File
import java.nio.file.Files.isDirectory
import java.nio.file.Files.walk
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors


val Project.xmePluginSpecService get() = this.service<XmePluginSpecService>()

@Service(Service.Level.PROJECT)
class XmePluginSpecService(val project: Project) {

    private val antMatcher = AntPathMatcher()
    @Volatile
    private var xmePluginSpec: XmePluginSpec = XmePluginSpec()
    private var embeddedSpec: XmePluginSpec = XmePluginSpec()

    // tenant -> spec -> file
    private val filesBySpec = ConcurrentHashMap<String, ConcurrentHashMap<String, MutableSet<PsiFile>>>()
    // tenant -> file -> spec
    private val specsByFile = ConcurrentHashMap<String, ConcurrentHashMap<String, MutableSet<Specification>>>()

    private val patterns = ConcurrentHashMap<String, ElementPattern<out PsiElement>>()

    fun initByEmbedConfig(xmePluginSpec: XmePluginSpec) {
        if (!project.isConfigProject()) {
            return
        }
        this.embeddedSpec = xmePluginSpec
        updateCustomConfig()
    }

    private fun reload(xmePluginSpec: XmePluginSpec, after: () -> Unit = {}) {
        project.doAsync {
            filesBySpec.clear()
            specsByFile.clear()
            joinSpecs(xmePluginSpec)
            loadAllFiles()

            filecache.clear()
            filesBySpec.forEach { (tenant, value) ->
                value.forEach { (spec, files) ->
                    files.forEach { file ->
                        InjectedLanguageManager.getInstance(project).dropFileCaches(file)
                        InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(file)
                    }
                }
            }
            PsiManager.getInstance(project).dropResolveCaches();
            JsonSchemaService.Impl.get(project).reset();
            DaemonCodeAnalyzer.getInstance(project).restart();
            after()
        }
    }

    fun updateCustomConfig(after: () -> Unit = {}) {
        val basePath = project.basePath ?: return
        val pathToConfig = basePath.trimEnd('/') + "/xme-plugin"
        if (!File(pathToConfig).exists()) {
            reload(XmePluginSpec())
            return
        }
        val registerKotlinModule = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val specs = File(pathToConfig).walk().toList().filter { it.extension == "yml" }
            .map { it.readText() }
            .map { registerKotlinModule.readValue<XmePluginSpec>(it) }
        val spec = if (specs.isEmpty()) XmePluginSpec() else specs.reduce { acc, x -> joinSpec(acc, x) }
        reload(spec, after)
    }

    fun parsePattern(pattern: String): ElementPattern<out PsiElement> {
        return patterns.computeIfAbsent(pattern) { it.toPsiPattern(true) }
    }

    private fun loadAllFiles() {
        val configDir = project.getConfigRootDir()
        val ymlFiles = findYmlFiles(configDir)
        ymlFiles.forEach { path ->
            val file = path.toString().toVirtualFile() ?: return@forEach
            fileAdded(file)
        }
        val pluginSpecs = findYmlFiles(project.basePath + "/xme-plugin")
        pluginSpecs.forEach { path ->
            val file = path.toString().toVirtualFile() ?: return@forEach
            fileAdded(file)
        }
    }

    fun fileAdded(file: VirtualFile) {
        project.doAsync {
            runReadAction {
                xmePluginSpec.specifications.filter { spec ->
                    spec.matchPath(project, file.path)
                }.forEach {
                    val tenant = file.getTenantName(project)
                    val tenantSpecs = filesBySpec.computeIfAbsent(tenant) { ConcurrentHashMap() }
                    val toPsiFile = file.toPsiFile(project) ?: return@forEach
                    tenantSpecs.computeIfAbsent(it.key) { mutableSetOf() }.add(toPsiFile)

                    specsByFile.computeIfAbsent(tenant) { ConcurrentHashMap() }
                        .computeIfAbsent(file.path) { mutableSetOf() }
                        .add(it)
                }
            }
        }
    }

    fun fileDeleted(path: String) {
        filesBySpec.forEach {
            val value: Map<String, MutableSet<PsiFile>> = it.value
            value.forEach {
                it.value.removeIf { file -> file.virtualFile?.path == path }
            }
        }
        specsByFile.forEach { it.value.remove(path) }
    }

    private fun joinSpecs(customSpec: XmePluginSpec) {
        val embeddedSpec = embeddedSpec
        val xmePluginSpec = joinSpec(embeddedSpec, customSpec)
        this.xmePluginSpec = xmePluginSpec
    }

    private fun joinSpec(
        first: XmePluginSpec,
        second: XmePluginSpec
    ): XmePluginSpec {
        val xmePluginSpec = XmePluginSpec()
        xmePluginSpec.specifications.addAll(first.specifications)
        xmePluginSpec.specifications.addAll(second.specifications)
        xmePluginSpec.jsFunctions.addAll(first.jsFunctions)
        xmePluginSpec.jsFunctions.addAll(second.jsFunctions)
        return xmePluginSpec
    }

    private fun findYmlFiles(startDir: String): List<Path> {
        walk(Paths.get(startDir)).use { stream ->
            return stream
                .filter { file -> !isDirectory(file) }
                .filter { file -> file.toString().endsWith(".yml") }
                .collect(Collectors.toList())
        }
    }

    fun getSpecifications(): List<Specification> {
        return xmePluginSpec.specifications
    }

    fun getSpecifications(psiFile: PsiFile?): List<Specification> {
        psiFile ?: return emptyList()
        psiFile.virtualFile ?: return emptyList()
        val tenant = psiFile.getTenantName()
        return specsByFile[tenant]?.get(psiFile.virtualFile?.path)?.toList() ?: emptyList()
    }

    fun getFiles(tenant: String, spec: String): List<PsiFile> {
        return filesBySpec[tenant]?.get(spec)?.toList() ?: emptyList()
    }

    fun getFunctions(includeFunctions: List<String>): String {
        return xmePluginSpec.jsFunctions
            .filter { includeFunctions.contains(it.key) }
            .joinToString("\n") { it.body ?: "" }
    }

}
