package com.icthh.xm.actions.settings

import com.icthh.xm.service.updateSupported
import com.icthh.xm.utils.logger
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.jetbrains.annotations.NotNull

class XmMenuGroup: DefaultActionGroup() {
    override fun actionPerformed(e: AnActionEvent) {}
    override fun update(e: AnActionEvent) {
        logger.info("Update main group ${e.updateSupported()}")
        e.updateSupported() ?: return
    }
}
