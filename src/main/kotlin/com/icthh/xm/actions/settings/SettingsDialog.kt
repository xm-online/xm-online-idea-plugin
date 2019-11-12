package com.icthh.xm.actions.settings

import com.icthh.xm.actions.VaadinDialog
import com.icthh.xm.service.getSettings
import com.intellij.openapi.project.Project
import com.vaadin.data.Binder
import com.vaadin.icons.VaadinIcons
import com.vaadin.navigator.View
import com.vaadin.server.Sizeable.Unit.PERCENTAGE
import com.vaadin.shared.ui.ValueChangeMode
import com.vaadin.ui.*
import com.vaadin.ui.themes.ValoTheme
import java.awt.Dimension
import kotlin.reflect.KMutableProperty1


class SettingsDialog(project: Project): VaadinDialog(
    project, "settings", Dimension(700, 350), "Settings"
) {

    val data = ArrayList(project.getSettings().envs)

    override fun view(): View = object: View {
        var env: EnvironmentSettings? = null

        override fun getViewComponent(): Component {

            val mainView = VerticalLayout()
            mainView.id = "mainView"

            val binder = Binder<EnvironmentSettings>()

            val horizontalLayout = HorizontalLayout()
            val envs = VerticalLayout()
            val form = VerticalLayout()
            horizontalLayout.addComponents(envs, form)

            mainView.setSizeFull()
            horizontalLayout.setSizeFull()
            mainView.addComponent(horizontalLayout)

            envs.setMargin(false)
            val buttons = HorizontalLayout()
            envs.addComponent(buttons)
            val add = Button()
            add.setIcon(VaadinIcons.PLUS)
            add.setStyleName(ValoTheme.BUTTON_LINK)

            val remove = Button()
            remove.setIcon(VaadinIcons.MINUS)
            remove.setStyleName(ValoTheme.BUTTON_LINK)
            remove.isEnabled = false
            buttons.addComponents(add, remove)
            buttons.isSpacing = true
            buttons.defaultComponentAlignment = Alignment.MIDDLE_LEFT

            val envSelect = ListSelect<EnvironmentSettings>()
            envSelect.setItems(data)
            envSelect.setRows(12);
            envSelect.setWidth(100.0f, PERCENTAGE);

            envSelect.addValueChangeListener{
                if (it.value.isNotEmpty() && !it.value.equals(setOf(env))) {
                    envSelect.deselectAll()
                    val last = it.value.last()
                    env = last
                    envSelect.select(last)
                    binder.bean = last
                }
                remove.isEnabled = true
                form.isEnabled = true
            }
            envs.addComponent(envSelect)

            add.addClickListener {
                var i = data.size
                i++
                while(data.filter { it.name == "env ${i}" }.isNotEmpty()) {
                    i++
                }
                val element = EnvironmentSettings()
                element.name = "env ${i}"
                data.add(element)
                envSelect.dataProvider.refreshAll()
            }
            remove.addClickListener {
                data.removeAll(envSelect.value)
                envSelect.dataProvider.refreshAll()
                envSelect.deselectAll()
                if (data.isEmpty()) {
                    remove.isEnabled = false
                }
                binder.bean = null
                form.isEnabled = false
            }

            val name = TextField("Name")
            val xmUrl = TextField("Xm base url (example: http://xm-online.com)")
            val login = TextField("Super admin login")
            val password = PasswordField("Super admin password")
            binder.bindField(name, EnvironmentSettings::name)
            binder.bindField(xmUrl, EnvironmentSettings::xmUrl)
            binder.bindField(login, EnvironmentSettings::xmSuperAdminLogin)
            binder.bindField(password, EnvironmentSettings::xmSuperAdminPassword)
            name.setSizeFull()
            xmUrl.setSizeFull()
            login.setSizeFull()
            password.setSizeFull()
            form.addComponents(name, xmUrl, login, password)

            name.valueChangeMode = ValueChangeMode.EAGER
            binder.addValueChangeListener {
                envSelect.dataProvider.refreshAll()
            }

            mainView.setMargin(false)
            form.isEnabled = false
            return mainView
        }

        private fun Binder<EnvironmentSettings>.bindField(
            name: AbstractField<String>,
            nameProperty: KMutableProperty1<EnvironmentSettings, String>
        ) {
            bind(name, nameProperty.getter, nameProperty.setter)
        }
    }


}

