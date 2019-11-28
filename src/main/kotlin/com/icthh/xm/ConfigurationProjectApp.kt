package com.icthh.xm


import com.icthh.xm.service.getSettings
import com.icthh.xm.service.saveCurrectFileStates
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataKeys
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileContentsChangedAdapter
import com.intellij.openapi.vfs.VirtualFileManager


class ConfigurationProjectApp: ProjectComponent {

    override fun initComponent() {

    }

    override fun disposeComponent() {

    }

    override fun projectOpened() {
        VirtualFileManager.getInstance().addVirtualFileListener(object: VirtualFileContentsChangedAdapter() {
            override fun onFileChange(fileOrDirectory: VirtualFile) {
                val dataContext = DataManager.getInstance().dataContext
                val project = DataKeys.PROJECT.getData(dataContext) ?: return
                //project.getSettings()?.selected()?.trackChanges = true
                // TODO project.saveCurrectFileStates()
            }

            override fun onBeforeFileChange(fileOrDirectory: VirtualFile) {
            }
        })
    }

    override fun projectClosed() {

    }

}
