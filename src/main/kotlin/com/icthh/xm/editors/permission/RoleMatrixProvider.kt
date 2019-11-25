package com.icthh.xm.editors.permission

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class RoleMatrixProvider: FileEditorProvider {

    override fun getEditorTypeId(): String {
        return "role-matrix"
    }

    override fun accept(project: Project, file: VirtualFile) =
        file.path.endsWith("/permissions.yml")


    override fun createEditor(project: Project, file: VirtualFile): FileEditor = RoleMatrixEditor(project, file)

    override fun getPolicy() = PLACE_AFTER_DEFAULT_EDITOR

}
