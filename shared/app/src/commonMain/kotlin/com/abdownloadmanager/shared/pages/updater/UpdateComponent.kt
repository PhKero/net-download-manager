package com.abdownloadmanager.shared.pages.updater

import com.abdownloadmanager.UpdateCheckStatus
import com.abdownloadmanager.shared.util.AppVersion
import com.abdownloadmanager.shared.util.BaseComponent
import com.abdownloadmanager.UpdateManager
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.pagemanager.NotificationSender
import com.abdownloadmanager.shared.ui.widget.MessageDialogType
import com.abdownloadmanager.shared.util.mvi.ContainsEffects
import com.abdownloadmanager.shared.util.mvi.supportEffects
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.tr
import com.abdownloadmanager.shared.ui.widget.NotificationType
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class UpdateComponent(
    ctx: ComponentContext,
    private val notificationSender: NotificationSender,
    private val updateManager: UpdateManager,
) : BaseComponent(ctx),
    ContainsEffects<UpdateComponent.Effects> by supportEffects(),
    KoinComponent {

    fun isUpdateSupported(): Boolean {
        return updateManager.isUpdateSupported()
    }

    val currentVersion = AppVersion.get()
    val showNewUpdate = MutableStateFlow(false)
    val newVersionData = updateManager.newVersionData
    private var updateApplierJob: Job? = null

    val updateCheckStatus = updateManager.updateCheckStatus

    fun performUpdate() {
        updateApplierJob?.cancel()
        updateApplierJob = scope.launch {
            try {
                updateManager.update()
            } catch (e: Exception) {
                showMessage(e)
            }
        }
    }

    private fun showMessage(e: Exception) {
        e.printStackTrace()
        notificationSender.sendDialogNotification(
            Res.string.update_error.asStringSource(),
            e.localizedMessage.orEmpty().asStringSource(),
            type = MessageDialogType.Error,
        )
    }

    fun showNewUpdate() {
        showNewUpdate.update { true }
    }

    fun requestCheckForUpdate() {
        scope.launch {
            updateManager.checkForUpdate()
        }
    }

    // a check the app makes by itself: a new version only shows a small notification, never a window
    private var quietCheck = false

    /** returns true when the check reached the server (so the caller can remember the date) */
    suspend fun quietCheckForUpdate(): Boolean {
        quietCheck = true
        return try {
            val info = updateManager.checkForUpdate()
            if (info != null) {
                notificationSender.sendNotification(
                    tag = "app-update",
                    title = tr("New version available", "فيه نسخة جديدة"),
                    description = tr(
                        "Version ${info.version} is ready. Help > Check for update to install it.",
                        "النسخة ${info.version} جاهزة. من Help > Check for update تقدر تسطبها.",
                    ),
                    type = NotificationType.Info,
                )
            }
            updateCheckStatus.value !is UpdateCheckStatus.Error
        } catch (e: Exception) {
            false
        } finally {
            // let the status listener below see the statuses of this check before it acts again
            delay(2000)
            quietCheck = false
        }
    }

    fun requestClose() {
        showNewUpdate.update { false }
    }

    init {
        updateCheckStatus
            .drop(1) // state flow
            .onEach {
                // a quiet check shows no "checking" / "no update" / error messages and no window
                if (quietCheck) return@onEach
                when (it) {
                    UpdateCheckStatus.Checking -> {
                        sendEffect(Effects.CheckingForUpdate)
                    }

                    is UpdateCheckStatus.Error -> {
                        sendEffect(Effects.Error(it.e))
                    }

                    UpdateCheckStatus.NewUpdate -> {
                        sendEffect(Effects.NewUpdate)
                        showNewUpdate()
                    }

                    UpdateCheckStatus.NoUpdate -> {
                        sendEffect(Effects.NoUpdate)
                    }

                    UpdateCheckStatus.IDLE -> {

                    }
                }
            }.launchIn(scope)
    }

    sealed interface Effects {
        data object CheckingForUpdate : Effects
        data object NoUpdate : Effects
        data object NewUpdate : Effects
        data class Error(val throwable: Throwable) : Effects
    }
}
