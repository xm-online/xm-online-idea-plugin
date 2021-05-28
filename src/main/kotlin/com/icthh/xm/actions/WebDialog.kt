package com.icthh.xm.actions

import com.icthh.xm.ViewServer
import com.icthh.xm.ViewServer.isDev
import com.icthh.xm.utils.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefAppRequiredArgumentsProvider
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.UIUtil
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.callback.CefSchemeHandlerFactory
import org.cef.handler.CefFocusHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.collections.HashMap


abstract class WebDialog(val project: Project,
                         val viewName: String,
                         val dimension: Dimension = Dimension(500, 500),
                         dialogTitle: String = "Dialog"): DialogWrapper(project) {

    val jCefWebPanelWrapper = JCefWebPanelWrapper(viewName, dimension, this.disposable)
    val centerPanel = lazy {
        jCefWebPanelWrapper.createCenterPanel({callbacks(it)}, {onReady(it)})
    }

    init {
        title = dialogTitle
    }

    override fun show() {
        this.init()
        super.show()
    }

    override fun createCenterPanel() = centerPanel.value

    abstract fun callbacks(browser: JBCefBrowser): List<BrowserCallback>

    open fun onReady(pipe: BrowserPipe) {}

    override fun shouldCloseOnCross() = true

    fun invokeOnUiThread(operation: () -> Unit) {
        jCefWebPanelWrapper.invokeOnUiThread { operation() }
    }

}

class JCefWebPanelWrapper(
    val viewName: String,
    val dimension: Dimension = Dimension(500, 500),
    val disposable: Disposable
) {

    /**
     *  WARNING, by strange issue JBCefBrowser locked when I try to call
     *  ApplicationManager.getApplication().invokeLater({ }, ModalityState.stateForComponent(browser.component))
     *  or
     *  ApplicationManager.getApplication().invokeLater({ }, ModalityState.stateForComponent(rootPane))
     *
     *  This label for avoid this issue
     */
    val uiThreadAnchor = JLabel(".");
    val pipeId: String = UUID.randomUUID().toString()

    init {
        ViewServer.startServer()
    }

    fun createCenterPanel(callbacks: (browser: JBCefBrowser) -> List<BrowserCallback>, onReady: (BrowserPipe) -> Unit = {}): JComponent {
        val url = "${ViewServer.getServerUrl()}/app?${pipeId}#/$viewName"
        val uiThreadAnchor = this.uiThreadAnchor

        val browser = JBCefBrowser(url)
        if (isDev) {
            //browser.openDevtools()
        }
        logger.info("URL load ${url}")
        val browserPipe = BrowserPipe(browser, pipeId, viewName, callbacks(browser), onReady)

        val panel = JPanel(BorderLayout())
        panel.preferredSize = dimension

        panel.add(browser.component, BorderLayout.CENTER);
        panel.add(uiThreadAnchor, BorderLayout.SOUTH)
        Disposer.register(this.disposable, browser)
        Disposer.register(this.disposable, browserPipe)

        logger.info("inited dialog")
        return panel
    }

    fun invokeOnUiThread(operation: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({
            operation.invoke();
        }, ModalityState.stateForComponent(uiThreadAnchor))
    }
}

class FocusHandlerStub: CefFocusHandlerAdapter()

/**
 * On linux for avoid crash gpu disabled.
 * issue: https://youtrack.jetbrains.com/issue/IDEA-248140
 * Note: this class used in plugin.xml
 */
class GpuDisabler: JBCefAppRequiredArgumentsProvider {
    override val options: List<String>
        get() = listOf("--disable-gpu", "--disable-gpu-compositing")
}

class BrowserPipe(private val browser: JBCefBrowser, pipeId: String, viewName: String, callbacks: List<BrowserCallback>, onReady: (BrowserPipe) -> Unit = {}) : Disposable {
    private val events = HashMap<String, JBCefJSQuery>()

    init {
        addBrowserEvents(WINDOW_READY_EVENT)
        callbacks.forEach { addBrowserEvents(it.name) }
        val theme = if (UIUtil.isUnderDarcula()) "dark-theme" else "light-theme"
        val initCallback = """
            const event = new Event("callbackReady");
            window.dispatchEvent(event);
            window.callbackReady = true;
            document.querySelector("body").classList.add('${theme}');            
        """.trimIndent()
        val js = inject() + "\n" + initCallback
        val instance = CefApp.getInstance()
        instance.registerSchemeHandlerFactory("http", "registercallback-${pipeId}", InjectJsHandlerFactory(js))
        if (isDev) {
            instance.registerSchemeHandlerFactory("http", "registercallback-${viewName}", InjectJsHandlerFactory(js))
        } else {
            instance.registerSchemeHandlerFactory("http", "registercallback-${viewName}", InjectJsHandlerFactory("console.log('PROD MODE');"))
        }
        callbacks.forEach { subscribe(it) }
        subscribe(BrowserCallback(WINDOW_READY_EVENT){_, pipe -> onReady(pipe)})
    }

    /**
     * Generate javascript code to register events.
     */
    private fun inject(): String {
        return events.entries.fold(StringBuilder()) { builder, (tag, query) ->
            builder.append(
                // language=JavaScript
                """
        window.messagePipe.ideApi.subscribe("$tag", function(data) {
          ${query.inject("data")}
        });
        """.trimIndent()
            )
        }.append("window.addEventListener(\"load\", () => {console.log(\"Init callbacks\");messagePipe.post(\"$WINDOW_READY_EVENT\");});").toString()
    }

    /**
     * Register events names. Any events added after execution of the browser-side code,
     * generated by [inject], will not be registered.
     */
    private fun addBrowserEvents(vararg names: String) {
        names.forEach {
            // TODO change when stable api will be provided
            @Suppress("UnstableApiUsage")
            events[it] = JBCefJSQuery.create(browser)
        }
    }

    /**
     * Post event [eventName] with [data] for the browser subscribers.
     *
     * Precondition: [eventName] should be registered with [addBrowserEvents] and
     * the code, generated by [inject], should be successfully executed in the browser.
     */
    fun post(eventName: String, data: String) {
        browser.cefBrowser.executeJavaScript(
            // language=JavaScript
            """
        window.messagePipe.ideApi.post("$eventName", $data);
      """.trimIndent(),
            null,
            0
        )
    }

    /**
     * Subscribe to [eventName], triggered from the browser-side code.
     *
     * Precondition: [eventName] should be registered with [addBrowserEvents].
     *
     * Events will not be triggered until the code, generated by [inject],
     * is not successfully executed in the browser.
     */
    private fun subscribe(callback: BrowserCallback) {
        val value = events[callback.name]
            ?: error("Could not subscribe to unregistered event with tag: ${callback.name}!")
        value.addHandler {
            callback.callback(it, this)
            null
        }
    }

    override fun dispose() {
        events.values.forEach(Disposer::dispose)
    }

    companion object {
        const val WINDOW_READY_EVENT = "documentReady"
    }
}

data class BrowserCallback(val name: String, val callback: (String, BrowserPipe) -> Unit)

class InjectJsHandlerFactory(val js: String): CefSchemeHandlerFactory {
    override fun create(
        cefBrowser: CefBrowser,
        cefFrame: CefFrame,
        s: String,
        cefRequest: CefRequest
    ): CefResourceHandler = ContentResourceHandler("application/javascript", js)
}

open class ContentResourceHandler(
    val mimeType: String,
    content: String
) : CefResourceHandlerAdapter() {

    private val myInputStream: InputStream
    override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
        callback.Continue()
        return true
    }

    override fun getResponseHeaders(response: CefResponse, response_length: IntRef, redirectUrl: StringRef) {
        response.mimeType = mimeType
        response.status = 200
    }

    override fun readResponse(
        data_out: ByteArray,
        bytes_to_read: Int,
        bytes_read: IntRef,
        callback: CefCallback
    ): Boolean {
        val availableSize = myInputStream.available()
        if (availableSize > 0) {
            var bytesToRead = Math.min(bytes_to_read, availableSize)
            bytesToRead = myInputStream.read(data_out, 0, bytesToRead)
            bytes_read.set(bytesToRead)
            return true
        }

        bytes_read.set(0)
        myInputStream.close()
        return false
    }

    init {
        myInputStream = ByteArrayInputStream(content.toByteArray(Charset.defaultCharset()))
    }
}
