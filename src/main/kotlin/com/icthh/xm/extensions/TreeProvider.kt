package com.icthh.xm.extensions

import com.icthh.xm.utils.log
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode

class TreeProvider: TreeStructureProvider {
    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<Any>>,
        settings: ViewSettings?
    ): MutableCollection<AbstractTreeNode<Any>> {
        return children
    }

}
