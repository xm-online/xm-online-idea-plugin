package com.icthh.xm.utils

import com.icthh.xm.actions.settings.SettingService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project

fun Project.getProperties() = PropertiesComponent.getInstance(this)

fun Project.getSettings() = ServiceManager.getService(this, SettingService::class.java)
