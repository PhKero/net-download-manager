package com.abdownloadmanager.desktop.storage

import com.abdownloadmanager.shared.storage.IExtraQueueSettings
import ir.amirab.util.desktop.poweraction.ContainsPowerActionConfigOnFinish
import ir.amirab.util.desktop.poweraction.PowerActionConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class DesktopExtraQueueSettings(
    override val id: Long,
    val powerActionTypeOnFinish: PowerActionConfig.Type? = null,
    val powerActionUseForceOnFinish: Boolean = false,
    // IDM: "open the following file when done" (a program, a .bat, any file); empty = nothing
    val openFileOnFinish: String = "",
    // IDM "number of retries for each file if downloading failed"; 0 = the app's general setting
    val retriesPerFile: Int = 0,
) : IExtraQueueSettings, ContainsPowerActionConfigOnFinish {

    override fun getPowerActionConfigOnFinish() = powerActionTypeOnFinish?.let {
        PowerActionConfig(
            powerActionTypeOnFinish,
            powerActionUseForceOnFinish,
        )
    }

    companion object : IExtraQueueSettings.DataClassDefinitions<DesktopExtraQueueSettings> {
        override fun createDefault(id: Long) = DesktopExtraQueueSettings(id)
        override val serializer: KSerializer<DesktopExtraQueueSettings> = DesktopExtraQueueSettings.serializer()
    }
}
