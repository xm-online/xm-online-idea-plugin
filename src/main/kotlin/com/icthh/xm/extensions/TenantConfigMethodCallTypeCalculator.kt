package com.icthh.xm.extensions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.icthh.xm.extensions.entityspec.originalFile
import com.icthh.xm.service.getConfigRootDir
import com.icthh.xm.service.getTenantName
import com.icthh.xm.utils.log
import com.icthh.xm.utils.logger
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import com.sun.codemodel.CodeWriter
import com.sun.codemodel.JCodeModel
import com.sun.codemodel.JType
import com.sun.codemodel.writer.SingleStreamCodeWriter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.util.childrenOfType
import org.jetbrains.plugins.groovy.lang.typing.DefaultMethodCallTypeCalculator
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator
import org.jsonschema2pojo.*
import org.jsonschema2pojo.rules.RuleFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*


class TenantConfigMethodCallTypeCalculator : GrTypeCalculator<GrMethodCall> {

    val TENANT_CONFIG = "com.icthh.xm.commons.config.client.service.TenantConfigService"
    val GET_CONFIG = "getConfig"
    val delegate: DefaultMethodCallTypeCalculator = DefaultMethodCallTypeCalculator()

    override fun getType(expression: GrMethodCall): PsiType? {

        if (
            TENANT_CONFIG.equals(expression.resolveMethod()?.containingClass?.qualifiedName)
            &&
            GET_CONFIG.equals(expression.callReference?.methodName)
        ) {
            val tenantName = expression.originalFile.virtualFile.getTenantName(expression.project)
            val source = generateClassesByTenant(expression.project, tenantName)
            val psiClass = JavaPsiFacade.getElementFactory(expression.project).createClassFromText("""
                ${source}                 
            """.trimIndent(), expression)

            val mainClass = psiClass.childrenOfType<PsiClass>().find {
                it.name == "${tenantName.toUpperCase()}TenantConfig"
            }
            mainClass ?: return delegate.getType(expression)
            return PsiTypesUtil.getClassType(mainClass)
        }

        return delegate.getType(expression)
    }

    fun convertYamlToJson(yaml: String): String {
        val yamlReader = ObjectMapper(YAMLFactory())
        val obj = yamlReader.readValue(yaml, Any::class.java)
        val jsonWriter = ObjectMapper()
        return jsonWriter.writeValueAsString(obj)
    }

    private fun generateClassesByTenant(project: Project, tenantName: String): String {
        val path = "${project.getConfigRootDir()}/tenants/${tenantName}/tenant-config.yml"
        val tenantConfig = VfsUtil.findFile(File(path).toPath(), true) ?: return ""
        val tenantConfigYml: String = LoadTextUtil.loadText(tenantConfig).toString()
        val configJson = convertYamlToJson(tenantConfigYml)

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
        val ruleFactory = RuleFactory(config, Jackson2Annotator(config), SchemaStore())
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
        var sourceCode = String(byteArray.toByteArray(), UTF_8)
        sourceCode = sourceCode.replace("public class", "public static class")
        sourceCode = sourceCode.replace("----------", "//--------")
        return sourceCode
    }
}
