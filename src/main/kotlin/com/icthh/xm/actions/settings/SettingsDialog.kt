package com.icthh.xm.actions.settings

import com.icthh.xm.actions.VaadinDialog
import com.icthh.xm.actions.shared.showNotification
import com.icthh.xm.service.getExternalConfigService
import com.icthh.xm.service.getLocalBranches
import com.icthh.xm.service.getRepository
import com.icthh.xm.service.getSettings
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.project.Project
import com.vaadin.data.Binder
import com.vaadin.data.HasValue
import com.vaadin.icons.VaadinIcons
import com.vaadin.server.Sizeable.Unit.PERCENTAGE
import com.vaadin.shared.ui.ValueChangeMode
import com.vaadin.ui.*
import com.vaadin.ui.themes.ValoTheme
import java.awt.Dimension
import kotlin.reflect.KMutableProperty1


class SettingsDialog(project: Project): VaadinDialog(
    project, "settings", Dimension(820, 600), "Settings"
) {

    var data = ArrayList<EnvironmentSettings>()
    var env: EnvironmentSettings? = null

    override fun component(): Component {

        data = ArrayList(project.getSettings().envs.map { it.copy() })

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
        envSelect.setRows(21);
        envSelect.setWidth(100.0f, PERCENTAGE)

        val success = Label("Success")
        val failed = Label("Failed")
        success.isVisible = false
        failed.isVisible = false

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
            success.isVisible = false
            failed.isVisible = false
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
        val clientId = TextField("Client id")
        val clientPassword = TextField("Client password")
        val updateMode = ComboBox<UpdateMode>("Update mode");
        updateMode.setItems(UpdateMode.values().asList())
        val startTrackChangesOnEdit = CheckBox("Start track changes on edit")
        val branchName = ComboBox<String>("Target branch name")
        branchName.setItems(project.getRepository().getLocalBranches())

        binder.bindField(name, EnvironmentSettings::name)
        binder.bindField(xmUrl, EnvironmentSettings::xmUrl)
        binder.bindField(login, EnvironmentSettings::xmSuperAdminLogin)
        binder.bindField(password, EnvironmentSettings::xmSuperAdminPassword)
        binder.bindField(clientId, EnvironmentSettings::clientId)
        binder.bindField(clientPassword, EnvironmentSettings::clientPassword)
        binder.bindField(updateMode, EnvironmentSettings::updateMode)
        binder.bindField(startTrackChangesOnEdit, EnvironmentSettings::startTrackChangesOnEdit)
        binder.bindField(branchName, EnvironmentSettings::branchName)

        name.setSizeFull()
        xmUrl.setSizeFull()
        login.setSizeFull()
        password.setSizeFull()
        clientId.setSizeFull()
        clientPassword.setSizeFull()
        val clientToken = HorizontalLayout()
        clientToken.addComponents(clientId, clientPassword)

        updateMode.addValueChangeListener {
            startTrackChangesOnEdit.isVisible = !(it.value?.isGitMode ?: true)
            branchName.isVisible = UpdateMode.GIT_BRANCH_DIFFERENCE == it.value
        }

        val checkConnection = HorizontalLayout()
        checkConnection.isSpacing = true
        val button = Button("Test connection")
        button.addClickListener {
            val env = this.env
            env ?: return@addClickListener
            try {
                project.getExternalConfigService().fetchToken(env)
                success.isVisible = true
                failed.isVisible = false
            } catch (e: Exception) {
                success.isVisible = false
                failed.isVisible = true
                project.showNotification("TestAuth", "Error test authentication", ERROR) {
                    e.message ?: ""
                }
            }
        }
        checkConnection.addComponents(button, success, failed)

        form.addComponents(name, xmUrl, login, password, clientToken, updateMode, startTrackChangesOnEdit, branchName, checkConnection)

        name.valueChangeMode = ValueChangeMode.EAGER
        binder.addValueChangeListener {
            envSelect.dataProvider.refreshAll()
        }

        mainView.setMargin(false)
        form.isEnabled = false
        return mainView
    }

    private inline fun <T> Binder<EnvironmentSettings>.bindField(
        name: HasValue<T>,
        nameProperty: KMutableProperty1<EnvironmentSettings, T>
    ) {
        bind(name, nameProperty.getter, nameProperty.setter)
    }
}
