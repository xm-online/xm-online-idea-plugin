package com.icthh.xm.extensions

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode

class TreeProvider: TreeStructureProvider {
    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>?>,
        settings: ViewSettings
    ): Collection<AbstractTreeNode<*>> {
        return children.filterNotNull()
    }
}
