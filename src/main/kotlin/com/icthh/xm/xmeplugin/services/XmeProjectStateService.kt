package com.icthh.xm.xmeplugin.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.xmeplugin.utils.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import toVirtualFile
import java.util.*
import java.util.function.BiFunction

@Service(Service.Level.PROJECT)
class XmeProjectStateService(val project: Project) {

    private val domainsFile = "${project.getConfigRootDir()}/tenants/tenant-domains.yml".toVirtualFile()
    private val aliasFile = "${project.getConfigRootDir()}/tenants/tenant-aliases.yml".toVirtualFile()
    private val domainsPsiFile = domainsFile?.toPsiFile(project)
    private val aliasPsiFile = aliasFile?.toPsiFile(project)

    private val domainsCache: TenantDomains?
        get() = domainsPsiFile?.withCache("tenantDomains") { buildDomainCache() }
    private val tenantAliasCache: TenantAliasTree?
        get() = aliasPsiFile?.withCache("tenantAliases") { buildTenantAliasCache() }

    fun getParentTenant(tenantName: String): String? {
        return tenantAliasCache?.getParent(tenantName)
    }

    private fun buildTenantAliasCache(): TenantAliasTree? {
        aliasFile ?: return null
        aliasFile.refresh(true, false)
        try {
            return YAML_MAPPER.readValue<TenantAliasTree>(aliasFile.inputStream)
        } catch (e: Exception) {
            if (e is ControlFlowException) {
                throw e
            }
            log.error("Error while reading tenant aliases", e)
            return null
        }
    }

    fun getDomain(tenantName: String): String? {
        val tenantDomains = getTenantDomains(tenantName).map { it.substringBefore(".") }
        return if (tenantDomains.size > 1) {
            "[${tenantDomains.joinToString(", ")}]"
        } else {
            tenantDomains.firstOrNull()
        }
    }

    fun getTenantDomains(
        tenantName: String
    ): List<String> {
        val tenantDomains = domainsCache ?: return emptyList()
        val lowercaseTenantName = tenantName.lowercase()
        return tenantDomains.domains[lowercaseTenantName]?.filter { !it.startsWith(lowercaseTenantName) } ?: emptyList()
    }

    fun getTenantByDomain(
        domain: String
    ): List<String> {
        val tenantDomains = domainsCache ?: return emptyList()
        return tenantDomains.tenantByDomain(domain.lowercase())
    }

    private fun buildDomainCache(): TenantDomains? {
        try {
            domainsFile ?: return null
            domainsFile.refresh(true, false)
            return TenantDomains(
                YAML_MAPPER.readValue<Map<String, List<String>>>(domainsFile.inputStream)
            )
        } catch (e: Exception) {
            log.error("Error while reading tenant domains", e)
            return null
        }
    }

    data class TenantDomains(
        val domains: Map<String, List<String>>
    ) {
        private val tenantDomains: Map<String, List<String>>
        init {
            val tenantDomains = mutableMapOf<String, MutableList<String>>()
            domains.forEach { (key, values) ->
                values.forEach { value ->
                    val list = tenantDomains[value] ?: mutableListOf()
                    list.add(key)
                    tenantDomains[value] = list
                }
            }
            this.tenantDomains = tenantDomains
        }

        fun tenantByDomain(domain: String): List<String> {
            return tenantDomains[domain] ?: emptyList()
        }
    }
    data class TenantAliasTree(
        val tenantAliasTree: List<TenantAlias>
    ) {
        private val parents: Lazy<Map<String, String>> = lazy { initParents() }
        private fun traverse(operation: BiFunction<TenantAlias, TenantAlias, Unit>) {
            val tree = Objects.requireNonNullElse(tenantAliasTree, emptyList())
            tree.forEach { it.traverseChild(operation) }
        }
        private fun initParents(): Map<String, String> {
            val parents = mutableMapOf<String, String>()
            this.traverse { parent, child ->
                parents[child.key] = parent.key
            }
            return parents
        }
        fun getParent(key: String): String? {
            return parents.value[key]
        }
    }
    data class TenantAlias(
        val children: List<TenantAlias>?,
        val key: String
    ) {
        fun traverseChild(operation: BiFunction<TenantAlias, TenantAlias, Unit>) {
            val children = this.children ?: emptyList()
            for (child in children) {
                operation.apply(this, child)
                child.traverseChild(operation)
            }
        }
    }

}
