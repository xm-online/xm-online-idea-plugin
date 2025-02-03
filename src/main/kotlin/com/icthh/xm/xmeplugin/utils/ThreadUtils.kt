package com.icthh.xm.xmeplugin.utils

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

fun <R> doPseudoAsync(operation: Callable<R>):R {
    val futureTask = FutureTask<R>(operation)
    val t = Thread(futureTask)
    t.start()
    return futureTask.get()
}

fun Project.doAsync(action: Runnable): Future<*> {
    return getApplication().executeOnPooledThread(Runnable {
        try {
            action.run()
        } catch (e: Exception) {
            if (e is ControlFlowException) {
                throw e
            }
            showError(this, e)
        }
    })
}

private fun showError(
    project: Project,
    e: Exception
) {
    project.log.warn("Error update configuration", e)
    invokeLater {
        project.showErrorNotification("Error update configuration") {
            e.message ?: "Error update configuration"
        }
    }
}


fun invokeLater(callback: () -> Unit) {
    getApplication().invokeLater {
        callback()
    }
}

fun runWriteAction(callback: () -> Unit) {
    getApplication().runWriteAction {
        callback()
    }
}

fun runReadAction(callback: () -> Unit) {
    getApplication().runReadAction {
        callback()
    }
}
