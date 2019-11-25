package com.icthh.xm.actions.deploy

import com.icthh.xm.actions.settings.EnvironmentSettings
import com.icthh.xm.actions.shared.showMessage
import com.icthh.xm.service.*
import com.icthh.xm.utils.readTextAndClose
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.MessageType.INFO
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import java.io.File
import java.io.InputStream
import org.apache.http.client.ClientProtocolException
import org.apache.http.util.EntityUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.entity.ContentType
import org.apache.http.entity.ContentType.DEFAULT_BINARY
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.HttpMultipartMode.BROWSER_COMPATIBLE
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.CloseableHttpClient




class DeployToEnv() : AnAction() {

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        val project = anActionEvent.project
        project ?: return
        val selected = project.getSettings().selected()
        selected ?: return

        val changesFiles = project.getChangedFiles()

        val fileListDialog = FileListDialog(project, changesFiles)
        fileListDialog.show()
        if (fileListDialog.isOK) {
            project.updateFilesInMemory(changesFiles, selected)
        }

    }


    override fun update(anActionEvent: AnActionEvent) {
        super.update(anActionEvent)
        if (anActionEvent.updateSupported() == null) {
            return
        }
        val project = anActionEvent.project ?: return

        anActionEvent.presentation.isEnabled = project.getSettings().selected()?.trackChanges ?: false
    }
}
