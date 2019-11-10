package com.icthh.xm.editors.permission

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel

class PermissionFileEditor(file: VirtualFile): FileEditor {
    override fun isModified(): Boolean {
        return false;
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {

    }

    override fun getName(): String {
        return "Permission editor"
    }

    override fun setState(state: FileEditorState) {

    }

    override fun getComponent(): JComponent {
        return JLabel("hello world")
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return null
    }

    override fun <T : Any?> getUserData(key: Key<T>): T? {
        return null
    }

    override fun selectNotify() {

    }

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) {

    }

    override fun getCurrentLocation(): FileEditorLocation? {
        return null
    }

    override fun deselectNotify() {

    }

    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? {
        return null
    }

    override fun isValid(): Boolean {
        return true
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {

    }

    override fun dispose() {

    }

}
