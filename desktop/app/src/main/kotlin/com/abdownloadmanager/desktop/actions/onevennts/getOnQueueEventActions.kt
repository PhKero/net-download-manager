package com.abdownloadmanager.desktop.actions.onevennts

import com.abdownloadmanager.desktop.PowerActionManager
import ir.amirab.util.desktop.poweraction.PowerActionConfig
import com.abdownloadmanager.desktop.pages.poweractionalert.PowerActionComponent
import com.abdownloadmanager.desktop.storage.DesktopExtraQueueSettings
import com.abdownloadmanager.shared.storage.IExtraQueueSettingsStorage
import com.abdownloadmanager.shared.util.onqueuecompletion.OnQueueCompletionActionProvider
import com.abdownloadmanager.shared.util.onqueuecompletion.OnQueueEventAction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class DesktopOnQueueEventActionProvider(
    private val desktopExtraQueueSettingsStorage: IExtraQueueSettingsStorage<DesktopExtraQueueSettings>,
) : OnQueueCompletionActionProvider, KoinComponent {
    // TODO: BUG
    // at the moment if I move this to constructor the DI halts
    // probably due to Circular Dependency but no exception is thrown
    // I need to redesign the dependency graph to prevent these sorts of issues!
    private val powerActionManager: PowerActionManager by inject()

    override suspend fun getOnQueueEventActions(queueId: Long): List<OnQueueEventAction> {
        return desktopExtraQueueSettingsStorage.getExtraQueueSettings(queueId).let {
            buildList {
                // run the file first: a shut down after it would otherwise stop it from opening
                it.openFileOnFinish.takeIf { path -> path.isNotBlank() }?.let { path ->
                    add(OpenFileOnQueueFinish(path))
                }
                it.getPowerActionConfigOnFinish()?.let { powerAction ->
                    add(
                        PowerActionOnQueueFinishOrTimeEnd(
                            powerActionManager,
                            powerAction,
                        )
                    )
                }
            }
        }
    }
}

/** IDM: open a file or run a program when the queue has finished all its downloads */
class OpenFileOnQueueFinish(
    private val path: String,
) : OnQueueEventAction {
    override suspend fun onQueueCompleted(queueId: Long) {
        runCatching {
            val file = java.io.File(path.trim().trim('"'))
            if (!file.exists()) return
            // java.awt.Desktop.open refuses .bat files ("Unsupported URI content"): run programs ourselves
            when (file.extension.lowercase()) {
                "exe", "com" -> ProcessBuilder(file.path).directory(file.parentFile).start()
                "bat", "cmd" -> ProcessBuilder("cmd.exe", "/c", file.path).directory(file.parentFile).start()
                else -> java.awt.Desktop.getDesktop().open(file)
            }
        }.onFailure { it.printStackTrace() }
    }

    // like IDM: only when the work is done, not when the queue is stopped by its end time
    override suspend fun onQueueEndTimeReached(queueId: Long) = Unit
}

class PowerActionOnQueueFinishOrTimeEnd(
    private val powerActionManager: PowerActionManager,
    private val powerActionConfig: PowerActionConfig,
) : OnQueueEventAction {
    override suspend fun onQueueCompleted(queueId: Long) {
        powerActionManager.initiatePowerAction(
            powerActionConfig,
            PowerActionComponent.PowerActionReason.QueueWorkFinished
        )
    }

    override suspend fun onQueueEndTimeReached(queueId: Long) {
        powerActionManager.initiatePowerAction(
            powerActionConfig,
            PowerActionComponent.PowerActionReason.QueueEndTimeReached
        )
    }
}
