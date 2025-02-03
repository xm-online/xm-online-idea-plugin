package com.icthh.xm.xmeplugin.extensions

import com.icthh.xm.xmeplugin.services.XmeProjectStateService
import com.icthh.xm.xmeplugin.utils.getConfigRootDir
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import getTenantName

class TreeProvider: TreeStructureProvider {

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>?>,
        settings: ViewSettings
    ): Collection<AbstractTreeNode<*>> {
        if (parent is PsiDirectoryNode && "${parent.project.getConfigRootDir()}/tenants".equals(parent.virtualFile?.path)) {

            children.filterIsInstance<PsiDirectoryNode>().forEach { node ->
                val presentation = node.presentation
                val tenantName = node.virtualFile?.getTenantName(node.project) ?: return@forEach
                val service = node.project.service<XmeProjectStateService>()
                val tenantDomain = service.getDomain(tenantName)

                val parentTenant = service.getParentTenant(tenantName)
                val parentDomain = parentTenant?.let { service.getDomain(parentTenant) }

                if (tenantDomain != null || parentTenant != null) {
                    val tenantString = tenantDomain ?: tenantName.lowercase()
                    val parentString = parentTenant?.let { " -> ${parentDomain ?: parentTenant.lowercase()}" }
                    presentation.locationString = "$tenantString${parentString ?: ""}"
                }
            }
        }

        return children.filterNotNull()
    }






}
