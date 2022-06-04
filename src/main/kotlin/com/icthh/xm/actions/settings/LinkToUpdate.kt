package com.icthh.xm.actions.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class LinkToUpdate : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        BrowserUtil.browse("https://github.com/xm-online/xm-online-idea-plugin/releases")
    }
}
