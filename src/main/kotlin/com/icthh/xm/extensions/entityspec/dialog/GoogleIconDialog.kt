package com.icthh.xm.extensions.entityspec.dialog

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.DeserializationFeature.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.icthh.xm.actions.VaadinDialog
import com.intellij.openapi.project.Project
import com.vaadin.ui.Component
import com.vaadin.ui.VerticalLayout
import java.net.URL

class GoogleIconDialog(project: Project): VaadinDialog(project, "material-icon-dialog") {

    override fun component(): Component {
        return VerticalLayout().apply {

        }
    }

}
