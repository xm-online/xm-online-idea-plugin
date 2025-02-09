package com.icthh.xm.xmeplugin.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.xmeplugin.utils.*
import com.icthh.xm.xmeplugin.yaml.YamlUtils
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.sun.codemodel.CodeWriter
import com.sun.codemodel.JCodeModel
import com.sun.codemodel.JPackage
import com.sun.codemodel.JType
import com.sun.codemodel.writer.SingleStreamCodeWriter
import getTenantName
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jsonschema2pojo.*
import org.jsonschema2pojo.rules.RuleFactory
import org.jsonschema2pojo.util.NameHelper
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

const val TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME = "_PluginTenantConfigAutocomplete";
const val HIDDEN_FIELDS_FOR_XM_PLUGIN = "_hidden_for_plugin_"

val TENANT_CONFIG_FIELD = Key.create<Boolean>("TENANT_CONFIG_FIELD")
val TENANT_CONFIG_FIELD_PATH = Key.create<MutableList<String>>("TENANT_CONFIG_FIELD_PATH")

val Project.tenantConfigService get() = this.service<TenantConfigService>()

@Service(Service.Level.PROJECT)
class TenantConfigService {

    data class FieldHolder(
        val name: String,
        val technicalName: String,
        val psiType: PsiType,
        val navigationElement: PsiField,
        val internal: Boolean
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
        val tenantName = virtualFile.getTenantName(project).uppercase()
        val path = "${project.getConfigRootDir()}/tenants/${tenantName}/tenant-config.yml"
        val tenantConfig = VfsUtil.findFile(File(path).toPath(), true)
        val tenantConfigFile = tenantConfig?.toPsiFile(project)
        return tenantConfigFile?.withCache("$tenantName-tenant-config-yaml-type") {
            log.info("Calculate psi type for tenant config ${tenantName}")
            val tenantConfigYml: String = tenantConfig.let { LoadTextUtil.loadText(tenantConfig).toString() } ?: ""
            return@withCache calculatePsiType(tenantName, tenantConfigFile, tenantConfigYml, project)
        }
    }

    private fun calculatePsiType(
        tenantName: String,
        tenantConfigFile: PsiFile,
        tenantConfigYml: String,
        project: Project
    ): PsiClassType? {
        val variables: MutableMap<String, MutableList<FieldHolder>> = HashMap()
        val nameMap: MutableMap<String, String> = HashMap()
        var source = generateClassesByTenant(tenantName, tenantConfigYml, nameMap)
        source = source.replace("package XM.entity.lep.commons;", "")
        source = source.replace("import java.util.ArrayList;", "")
        source = source.replace("import java.util.List;", "")
        source = source.replace("import com.fasterxml.jackson.annotation.JsonInclude;", "")
        source = source.replace("import com.fasterxml.jackson.annotation.JsonProperty;", "")
        source = source.replace("import com.fasterxml.jackson.annotation.JsonPropertyOrder;", "")

        val className = "${tenantName.uppercase()}$TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME"
        val psiFile = PsiFileFactory.getInstance(project)
            .createFileFromText(
                className + JavaFileType.INSTANCE.getDefaultExtension(),
                JavaFileType.INSTANCE,
                """ package XM.entity.lep.commons;

                    import java.util.ArrayList;
                    import java.util.List;
                    import com.fasterxml.jackson.annotation.JsonInclude;
                    import com.fasterxml.jackson.annotation.JsonProperty;
                    import com.fasterxml.jackson.annotation.JsonPropertyOrder;
                    
                    class $className { 
                        ${source} 
                    } """.trimMargin()
            ) as PsiJavaFile


        val rootClass = psiFile.classes[0]
        val mainClass = rootClass.childrenOfType<PsiClass>().find {
            it.name == "${tenantName.uppercase()}TenantConfig${TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME}"
        }
        mainClass ?: return null
        rootClass.childrenOfType<PsiClass>().forEach { psiClass ->
            val additionalVariables = ArrayList<FieldHolder>()
            variables.put(psiClass.name ?: "", additionalVariables)
            psiClass.fields.forEach { field ->

                field.putUserData(TENANT_CONFIG_FIELD, true)

                if (field.name.startsWith(HIDDEN_FIELDS_FOR_XM_PLUGIN)) {
                    val fieldName = nameMap[field.name]
                    fieldName?.let {
                        additionalVariables.add(FieldHolder(fieldName, field.name, field.type, field, true))
                    }
                    if (fieldName != null) {
                        val manager: PsiManager = field.manager
                        field.nameIdentifier.replace(LightIdentifier(manager, "$fieldName"))
                    }
                }
            }
        }
        setPathYamlFields(mainClass)
        val type = PsiTypesUtil.getClassType(mainClass)
        val tenantTypeCache = TenantTypeCache(tenantConfigYml, type, variables)
        tenantConfigCache.put(tenantName, tenantTypeCache)
        return type
    }

    private fun setPathYamlFields(type: PsiClass, path: String = "") {
        type.fields.forEach { field ->
            field.putUserData(TENANT_CONFIG_FIELD_PATH, mutableListOf())
            if (field.type is PsiClassType) {
                val psiClassType = field.type as PsiClassType
                val psiClass = psiClassType.resolve()
                if (psiClass != null && psiClass.name?.endsWith(TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME).isTrue) {
                    setPathYamlFields(psiClass, "$path.${field.name}")
                } else if (psiClassType.parameters.isNotEmpty()) {
                    val genericType = psiClassType.parameters[0]
                    genericType.resolve()?.type()?.resolve()?.let {
                        if (it.name?.endsWith(TENANT_CONFIG_AUTO_GENERATE_CLASS_NAME).isTrue) {
                            setPathYamlFields(it, "$path.${field.name}[]")
                        }
                    }
                }
                field.getUserData(TENANT_CONFIG_FIELD_PATH)?.add("$path.${field.name}")
            }
        }
    }

    fun convertYamlToJson(yaml: String): String {
        val obj = YamlUtils.readYaml(yaml)
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
                        return " " + filterPropertyName(jsonFieldName, nameMap)
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

        log.info("Run generation")
        val type: JType = try {
            val filterJson = filterJson(configJson, nameMap)
            mapper.generate(
                codeModel,
                "${tenantName.uppercase()}TenantConfig",
                "${tenantName.uppercase()}.entity.lep.commons",
                filterJson
            )
        } catch (e: Exception) {
            log.error(e.toString(), e)
            return ""
        }
        log.info("Generated type ${type}")

        val byteArray = ByteArrayOutputStream()
        val out: CodeWriter = SingleStreamCodeWriter(byteArray)
        codeModel.build(out)
        var sourceCode = String(byteArray.toByteArray(), StandardCharsets.UTF_8)
        sourceCode = sourceCode.replace("public class", "public static class")
        sourceCode = sourceCode.replace("----------", "//--------")
        return sourceCode
    }

    private fun filterJson(json: String, fieldMap: MutableMap<String, String>): String {
        val mapper = ObjectMapper()
        val node = mapper.readValue<Map<*,*>>(json)
        val filteredNode = YamlUtils.transformJson(node) { filterPropertyName(it, fieldMap) }
        return mapper.writeValueAsString(filteredNode)
    }

    val fieldNameRegexp = "^[a-zA-ZА-Яа-я_$].*".toRegex()
    val fieldNameRegexp2 = "[0-9a-zA-ZА-Яа-я_\$]+".toRegex()

    private fun filterPropertyName(
        jsonFieldName: String?,
        nameMap: MutableMap<String, String>
    ): String {
        if (jsonFieldName.isNullOrBlank()) {
            val varname = "$HIDDEN_FIELDS_FOR_XM_PLUGIN${counter.incrementAndGet()}"
            nameMap.put(varname, jsonFieldName ?: "")
            return varname
        }
        if (jsonFieldName.matches(fieldNameRegexp) && jsonFieldName.matches(fieldNameRegexp2)) {
            return jsonFieldName
        }
        val varname = "$HIDDEN_FIELDS_FOR_XM_PLUGIN${counter.incrementAndGet()}"
        nameMap.put(varname, jsonFieldName)
        return varname
    }

    private fun readContent(project: Project, tenantName: String): String {
        val path = "${project.getConfigRootDir()}/tenants/${tenantName}/tenant-config.yml"
        val tenantConfig = VfsUtil.findFile(File(path).toPath(), true)
        val tenantConfigYml: String = tenantConfig?.let { LoadTextUtil.loadText(tenantConfig).toString() } ?: ""
        return tenantConfigYml
    }

    public fun getTenantConfigServiceType(project: Project): PsiType {
        val fqName = "com.icthh.xm.commons.config.client.service.TenantConfigService"
        val psiClass = JavaPsiFacade.getInstance(project).findClass(fqName, GlobalSearchScope.allScope(project))
        if (psiClass != null) {
            return JavaPsiFacade.getElementFactory(project).createType(psiClass)
        }

        val classText = """
            package com.icthh.xm.commons.config.client.service;

            import java.util.Map;

            public class TenantConfigService {
                public Map<String, Object> getConfig() {
                    return null;
                }
            }
        """.trimIndent()
        val psiFileFactory = PsiFileFactory.getInstance(project)
        val psiFile = psiFileFactory.createFileFromText(
            "TenantConfigService.java",
            JavaLanguage.INSTANCE,
            classText
        )
        val syntheticClass = PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java)
            ?: error("Failed to create synthetic TenantConfigService class")
        return JavaPsiFacade.getElementFactory(project).createType(syntheticClass)
    }

}
