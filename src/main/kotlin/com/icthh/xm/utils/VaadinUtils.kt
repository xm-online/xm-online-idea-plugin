package com.icthh.xm.utils

import com.vaadin.ui.Component
import com.vaadin.ui.ComponentContainer

inline fun <reified T: ComponentContainer> T.add(block: Component.() -> Unit) {

}

