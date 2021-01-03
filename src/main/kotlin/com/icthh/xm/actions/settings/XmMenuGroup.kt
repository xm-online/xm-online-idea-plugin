package com.icthh.xm.actions.settings

import com.icthh.xm.service.updateSupported
import com.icthh.xm.utils.logger
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.jetbrains.annotations.NotNull

class XmMenuGroup: DefaultActionGroup() {
    override fun actionPerformed(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
        e.updateSupported() ?: return
        logger.info("Update main group ${e.updateSupported()}")
    }
}
