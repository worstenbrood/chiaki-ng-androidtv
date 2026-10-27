package com.chiaking.tv.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.R
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.ui.components.settings.*
import com.chiaking.tv.ui.theme.*
@Composable
internal fun SettingsPanelScope.HudSettingsPanel() {
    SettingsCard(SurfaceFrosted) {
        TvCycleRow(
            title = stringResource(R.string.hud_shortcut),
            value = viewModel.menuShortcut.title,
            onNext = {
                val values = Preferences.MenuShortcut.values()
                viewModel.menuShortcut = values[(viewModel.menuShortcut.ordinal + 1) % values.size]
            },
            focusRequester = fr(1), onFocus = fb(1)
        )
        TvOrderToggleList(
            entries = viewModel.hudItems.map { TvOrderEntry(it.kind, stringResource(it.kind.titleRes), it.enabled, it.kind != Preferences.HudItemKind.CLOSE) },
            summary = stringResource(R.string.hud_items_summary),
            onToggle = { i ->
                if(viewModel.hudItems[i].kind != Preferences.HudItemKind.CLOSE)
                {
                    viewModel.hudItems = viewModel.hudItems.mapIndexed { j, e -> if(j == i) e.copy(enabled = !e.enabled) else e }
                }
            },
            onMove = { i, d ->
                val j = i + d
                if(j in viewModel.hudItems.indices)
                {
                    val list = viewModel.hudItems.toMutableList()
                    list.add(j, list.removeAt(i))
                    viewModel.hudItems = list
                }
            },
            focusRequester = fr(2), onFocus = fb(2)
        )
    }
}
