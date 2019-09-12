package com.icthh.xm

import com.vaadin.annotations.VaadinServletConfiguration
import com.vaadin.server.VaadinServlet
import javax.servlet.annotation.WebServlet

@WebServlet(urlPatterns = ["/*"], name = "AppServlet", asyncSupported = true)
@VaadinServletConfiguration(ui = AppUI::class, productionMode = false)
class AppServlet : VaadinServlet()
