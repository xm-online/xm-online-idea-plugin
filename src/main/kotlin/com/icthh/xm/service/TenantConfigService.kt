package com.icthh.xm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.icthh.xm.utils.childrenOfType
import com.icthh.xm.utils.log
import com.icthh.xm.utils.logger
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.psi.util.PsiTypesUtil
import com.sun.codemodel.CodeWriter
import com.sun.codemodel.JCodeModel
import com.sun.codemodel.JPackage
import com.sun.codemodel.JType
import com.sun.codemodel.writer.SingleStreamCodeWriter
import org.jsonschema2pojo.*
import org.jsonschema2pojo.rules.RuleFactory
import org.jsonschema2pojo.util.NameHelper
import java.awt.SystemColor.text
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

const val TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME = "_PluginTenantConfigAutocomplete";
const val HIDDEN_FIELDS_FOR_XM_PLUGIN = "_hidden_for_plugin_"

class TenantConfigService {

    data class FieldHolder(
        val name: String,
        val psiType: PsiType,
        val navigationElement: PsiField
    )
    data class TenantTypeCache(
        val content: String,
        val psiType: PsiType,
        val variables: Map<String, MutableList<FieldHolder>>
    )
    private val tenantConfigCache: MutableMap<String, TenantTypeCache> = ConcurrentHashMap()
    private val counter: AtomicInteger = AtomicInteger(0);

    fun getFields(tenantName: String, classSimpleName: String): List<FieldHolder> {
        val tenantTypeCache = tenantConfigCache[tenantName] ?: return listOf()
        val fields = tenantTypeCache.variables[classSimpleName] ?: return listOf()
        return fields
    }

    fun getPsiTypeForExpression(
        virtualFile: VirtualFile,
        project: Project,
        context: PsiElement
    ): PsiType? {
        val tenantName = virtualFile.getTenantName(project).toUpperCase()
        val tenantConfigYml: String = readContent(project, tenantName)

        if (tenantConfigCache.contains(tenantName)) {
            val cache = tenantConfigCache.get(tenantName)
            if (tenantConfigYml.equals(cache?.content)) {
                return cache?.psiType
            }
        }

        val variables: MutableMap<String, MutableList<FieldHolder>> = HashMap()
        val nameMap: MutableMap<String, String> = HashMap()
        val source = generateClassesByTenant(tenantName, tenantConfigYml, nameMap)

        val className = "${tenantName.toUpperCase()}$TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME"
        val psiFile = PsiFileFactory.getInstance(project)
            .createFileFromText(
                className + JavaFileType.INSTANCE.getDefaultExtension(),
                JavaFileType.INSTANCE,
                " class $className { \n ${source} \n } "
            ) as PsiJavaFile


        val rootClass = psiFile.classes[0]
        val mainClass = rootClass.childrenOfType<PsiClass>().find {
            it.name == "${tenantName.toUpperCase()}TenantConfig${TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME}"
        }
        mainClass ?: return null
        rootClass.childrenOfType<PsiClass>().forEach { psiClass ->
            val additionalVariables = ArrayList<FieldHolder>()
            variables.put(psiClass.name ?: "", additionalVariables)
            psiClass.fields.forEach { field ->
                if (field.name.startsWith(HIDDEN_FIELDS_FOR_XM_PLUGIN)) {
                    val fieldName = nameMap[field.name]
                    fieldName?.let { additionalVariables.add(FieldHolder(fieldName, field.getType(), field)) }
                    //field.delete()
                    if (fieldName != null) {
                        val manager: PsiManager = field.getManager()
                        // TODO replace by reference contributor
                        field.nameIdentifier.replace(LightIdentifier(manager, "${fieldName}"))
                        //field.modifierList?.setModifierProperty("public", false)
                        //field.modifierList?.setModifierProperty("private", true)
                    }
                }
            }
        }
        val type = PsiTypesUtil.getClassType(mainClass)
        val tenantTypeCache = TenantTypeCache(tenantConfigYml, type, variables)
        tenantConfigCache.put(tenantName, tenantTypeCache)
        return type
    }

    fun convertYamlToJson(yaml: String): String {
        val yamlReader = ObjectMapper(YAMLFactory())
        val obj = yamlReader.readValue(yaml, Any::class.java)
        val jsonWriter = ObjectMapper()
        return jsonWriter.writeValueAsString(obj)
    }

    private fun generateClassesByTenant(
        tenantName: String,
        tenantConfigYml: String,
        nameMap: MutableMap<String, String>
    ): String {

        val configJson = try {
            convertYamlToJson(tenantConfigYml)
        } catch (e: Exception) {
            "{}"
        }

        val codeModel = JCodeModel()
        val config: GenerationConfig = object: DefaultGenerationConfig() {
            override fun getSourceType() = SourceType.JSON
            override fun getAnnotationStyle() = AnnotationStyle.NONE
            override fun isIncludeAdditionalProperties() = false
            override fun getInclusionLevel() = InclusionLevel.ALWAYS
            override fun isIncludeGetters() = false
            override fun isIncludeSetters() = false
            override fun isIncludeHashcodeAndEquals() = false
            override fun isIncludeToString() = false
            override fun isIncludeGeneratedAnnotation() = false
        }
        val ruleFactory = object: RuleFactory(config, Jackson2Annotator(config), SchemaStore()) {
            override fun getNameHelper(): NameHelper {
                return object: NameHelper(config) {
                    override fun getPropertyName(jsonFieldName: String?, node: JsonNode?): String {
                        if (jsonFieldName == null || jsonFieldName.isBlank()) {
                            val varname = "$HIDDEN_FIELDS_FOR_XM_PLUGIN${counter.incrementAndGet()}"
                            nameMap.put(varname, jsonFieldName ?: "")
                            return " $varname"
                        }
                        if (jsonFieldName.matches("^[a-zA-ZА-Яа-я_$].*".toRegex()) && jsonFieldName.matches("[0-9a-zA-ZА-Яа-я_\$]+".toRegex())) {
                            return " " + jsonFieldName
                        }
                        val varname = "$HIDDEN_FIELDS_FOR_XM_PLUGIN${counter.incrementAndGet()}"
                        nameMap.put(varname, jsonFieldName)
                        return " $varname"
                    }

                    override fun getUniqueClassName(nodeName: String?, node: JsonNode?, _package: JPackage?): String {
                        val uniqueClassName = super.getUniqueClassName(nodeName, node, _package)
                        return uniqueClassName + TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME
                    }

                    override fun capitalizeTrailingWords(name: String): String {
                        val value = super.capitalizeTrailingWords(name)
                        return value.ifEmpty { "_${counter.incrementAndGet()}" }
                    }
                }
            }
        }
        val mapper = SchemaMapper(ruleFactory, SchemaGenerator())

        logger.info("Run generation")
        val type: JType = try {
            mapper.generate(
                codeModel,
                "${tenantName.toUpperCase()}TenantConfig",
                "${tenantName.toUpperCase()}.entity.lep.commons",
                configJson
            )
        } catch (e: Exception) {
            log.error(e.toString(), e)
            return ""
        }
        logger.info("Generated type ${type}")

        val byteArray = ByteArrayOutputStream()
        val out: CodeWriter = SingleStreamCodeWriter(byteArray)
        codeModel.build(out)
        var sourceCode = String(byteArray.toByteArray(), StandardCharsets.UTF_8)
        sourceCode = sourceCode.replace("public class", "public static class")
        sourceCode = sourceCode.replace("----------", "//--------")
        return sourceCode
    }

    private fun readContent(project: Project, tenantName: String): String {
        val path = "${project.getConfigRootDir()}/tenants/${tenantName}/tenant-config.yml"
        val tenantConfig = VfsUtil.findFile(File(path).toPath(), true)
        val tenantConfigYml: String = tenantConfig?.let { LoadTextUtil.loadText(tenantConfig).toString() } ?: ""
        return tenantConfigYml
    }

}
