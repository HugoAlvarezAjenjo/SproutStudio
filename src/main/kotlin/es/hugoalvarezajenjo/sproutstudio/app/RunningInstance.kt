package es.hugoalvarezajenjo.sproutstudio.app

import es.hugoalvarezajenjo.sproutstudio.platform.SingleInstance
import java.io.File
import javax.swing.SwingUtilities

/**
 * Makes this launch the running app, or hands [files] to the one already running. False means
 * the running app has them and this launch should end. macOS keeps one instance by itself
 * (`Platform.instanceDir` is null there), so on macOS this is always true.
 */
internal fun becomeRunningInstance(graph: AppGraph, files: List<File>): Boolean {
    val dir = graph.platform.instanceDir() ?: return true
    val role = SingleInstance(dir).start(files) { received ->
        SwingUtilities.invokeLater { graph.windows.openFromAnotherLaunch(received, graph.platform::bringToFront) }
    }
    // The OS frees the lock when the process ends anyway; this also removes the socket file.
    if (role is SingleInstance.Role.Primary) Runtime.getRuntime().addShutdownHook(Thread(role::close))
    return role != SingleInstance.Role.Forwarded
}
