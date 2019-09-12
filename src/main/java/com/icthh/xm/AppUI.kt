package com.icthh.xm

import com.icthh.xm.ViewRegistry.fillNavigator
import com.vaadin.annotations.Push
import com.vaadin.annotations.Theme
import com.vaadin.navigator.Navigator
import com.vaadin.navigator.View
import com.vaadin.server.VaadinRequest
import com.vaadin.shared.ui.ui.Transport
import com.vaadin.ui.Button
import com.vaadin.ui.Component
import com.vaadin.ui.UI
import com.vaadin.ui.themes.ValoTheme

@Push(transport = Transport.WEBSOCKET)
@Theme(ValoTheme.THEME_NAME)
class AppUI : UI() {

    override fun init(request: VaadinRequest) {
        navigator = Navigator(this, this)
        fillNavigator(navigator)
        navigator.addView("", object: View {
            override fun getViewComponent(): Component {
                val button = Button("navigate")
                button.addClickListener {
                    navigator.navigateTo("settings")
                }
                return button
            }
        })
    }

}
