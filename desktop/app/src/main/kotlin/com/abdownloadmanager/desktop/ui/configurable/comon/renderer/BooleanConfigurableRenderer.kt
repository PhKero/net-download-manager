package com.abdownloadmanager.desktop.ui.configurable.comon.renderer

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.abdownloadmanager.desktop.ui.configurable.ConfigTemplate
import com.abdownloadmanager.shared.ui.configurable.ConfigurableRenderer
import com.abdownloadmanager.desktop.ui.configurable.TitleAndDescription
import com.abdownloadmanager.shared.ui.configurable.ConfigurableUiProps
import com.abdownloadmanager.shared.ui.configurable.isConfigEnabled
import com.abdownloadmanager.shared.ui.configurable.item.BooleanConfigurable
import com.abdownloadmanager.shared.ui.widget.CheckBox
import com.abdownloadmanager.shared.ui.widget.Switch

object BooleanConfigurableRenderer : ConfigurableRenderer<BooleanConfigurable> {
    @Composable
    override fun RenderConfigurable(configurable: BooleanConfigurable, configurableUiProps: ConfigurableUiProps) {
        RenderBooleanConfig(configurable, configurableUiProps)
    }

    @Composable
    private fun RenderBooleanConfig(
        cfg: BooleanConfigurable,
        configurableUiProps: ConfigurableUiProps,
    ) {
        val checked = cfg.stateFlow.collectAsState().value
        val setValue = cfg::set
        val enabled = isConfigEnabled()
        // classic look: a check box in front of the option name
        androidx.compose.foundation.layout.Row(
            configurableUiProps.modifier.padding(configurableUiProps.itemPaddingValues),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            CheckBox(
                value = checked,
                enabled = enabled,
                size = 14.dp,
                onValueChange = {
                    setValue(it)
                }
            )
            TitleAndDescription(cfg)
        }
    }
}
