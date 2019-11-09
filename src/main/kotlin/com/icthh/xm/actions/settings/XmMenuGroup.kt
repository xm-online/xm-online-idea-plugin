package com.icthh.xm.actions.settings

import com.icthh.xm.utils.updateSupported
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup

class XmMenuGroup: DefaultActionGroup() {
    override fun actionPerformed(e: AnActionEvent) {}
    override fun update(e: AnActionEvent) {
        e.updateSupported() ?: return
    }
}
