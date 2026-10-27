// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.settings

import com.chiaking.tv.ui.screens.psn.PsnLoginDialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chiaking.tv.R
import com.chiaking.tv.common.psn.PsnAccountRepository
import com.chiaking.tv.ui.focus.FocusMemory
import com.chiaking.tv.ui.components.PinTextField
import com.chiaking.tv.ui.components.dialogs.PsAlertDialog
import com.chiaking.tv.ui.components.ScreenHeader
import com.chiaking.tv.ui.focus.rememberFocusRestore
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.PsBlueLight
import com.chiaking.tv.ui.focus.tryRequestFocus

// Keep in sync with CHIAKI_VERSION in external/chiaki-ng/CMakeLists.txt (see docs/UPSTREAM_SYNC.md)
const val UPSTREAM_CHIAKI_VERSION = "1.10.0"

@Composable
internal fun SettingsScreen(
	viewModel: SettingsViewModel,
	onBack: () -> Unit,
	onShowLogs: () -> Unit,
	onManageHosts: () -> Unit,
	onExportSettings: () -> Unit,
	onImportSettings: () -> Unit
)
{
    val ctx = LocalContext.current
    var removePsnAccount by remember { mutableStateOf<String?>(null) }
    var showPsnLogin by remember { mutableStateOf(false) }
    var showCoopLogin by remember { mutableStateOf(false) }
    var showPinEdit by remember { mutableStateOf(false) }
    var pinDraft by remember { mutableStateOf(viewModel.loginPin) }
	var category by remember { mutableStateOf(
		SettingsCategory.entries.getOrElse(FocusMemory.get("settings_cat")) { SettingsCategory.STREAM }) }
	LaunchedEffect(category) { viewModel.onCategoryChanged(category) }
	val restoreRow = FocusMemory.get("settings_row_${category.ordinal}")

	val navFocus = remember { FocusRequester() }
	val rowFocus = remember { FocusRequester() }
	val backFocus = remember { FocusRequester() }
	var rightFocused by remember { mutableStateOf(false) }
	BackHandler(enabled = rightFocused)
	{
		navFocus.tryRequestFocus()
	}
	rememberFocusRestore(restoreRow, navFocus, rowFocus)
	fun fr(i: Int): FocusRequester? {
		var target = restoreRow.coerceAtLeast(1)
		if(category == SettingsCategory.PSN) {
			val count = viewModel.psnAccounts.count { it.rid != viewModel.primaryAccountId }
			target = target.coerceAtMost(count + 2)
			if(count >= PsnAccountRepository.MAX_EXTRA_ACCOUNTS && target == 2) target = 3
		}
		return if(i == target) rowFocus else null
	}
	fun fb(i: Int): (Boolean) -> Unit = { if(it) FocusMemory.set("settings_row_${category.ordinal}", i) }

    val panel = SettingsPanelScope(viewModel, ::fr, ::fb, onShowLogs, onManageHosts,
        onExportSettings, onImportSettings,
        { pinDraft = viewModel.loginPin; showPinEdit = true },
        { showPsnLogin = true }, { showCoopLogin = true }, { removePsnAccount = it })
	Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
		ScreenHeader(
			stringResource(R.string.settings), onBack,
			modifier = Modifier.padding(horizontal = 40.dp),
			backFocusRequester = if(restoreRow == 0) backFocus else null,
			onBackFocus = { if(it) FocusMemory.set("settings_row_${category.ordinal}", 0) }
		)

		Spacer(Modifier.height(20.dp))

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.padding(horizontal = 40.dp)
		) {
			Column(
				modifier = Modifier
					.width(280.dp)
					.onKeyEvent { ev ->
						if(ev.key == Key.DirectionRight && ev.type == KeyEventType.KeyDown)
							true
						else
							false
					}
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(4.dp)
			) {
				SettingsCategory.entries.forEachIndexed { index, cat ->
					SettingsNavItem(
						label = stringResource(cat.titleRes),
						icon = cat.icon,
						selected = cat == category,
						focusRequester = if(index == category.ordinal) navFocus else null,
						onFocus = {
							if(it)
							{
								FocusMemory.set("settings_cat", index)
								category = cat
							}
						},
						onClick = {
							category = cat
							rowFocus.tryRequestFocus()
						}
					)
				}
			}

			Spacer(Modifier.width(28.dp))

			Column(
				modifier = Modifier
					.weight(1f)
					.onFocusEvent { rightFocused = it.hasFocus }
					.onKeyEvent { ev ->
						if(ev.key == Key.DirectionLeft && ev.type == KeyEventType.KeyDown)
							true
						else
							false
					}
					.verticalScroll(rememberScrollState())
			) {
				when(category)
				{
					SettingsCategory.STREAM -> with(panel) { StreamSettingsPanel() }
					SettingsCategory.AUDIO -> with(panel) { AudioSettingsPanel() }
					SettingsCategory.MIC -> with(panel) { MicSettingsPanel() }
					SettingsCategory.CONTROLLER -> with(panel) { ControllerSettingsPanel() }
					SettingsCategory.HUD -> with(panel) { HudSettingsPanel() }
					SettingsCategory.GENERAL -> with(panel) { GeneralSettingsPanel() }
					SettingsCategory.PSN -> with(panel) { PsnSettingsPanel() }
					SettingsCategory.BACKUP -> with(panel) { BackupSettingsPanel() }
					SettingsCategory.ABOUT -> with(panel) { AboutSettingsPanel() }

				}
				Spacer(Modifier.height(32.dp))
			}
		}
	}

	if(showPsnLogin)
		PsnLoginDialog(
			onDismiss = { showPsnLogin = false },
			onLoggedIn = { showPsnLogin = false; navFocus.tryRequestFocus() }
		)

	if(showCoopLogin)
		PsnLoginDialog(
			asExtra = true,
			onDismiss = { showCoopLogin = false },
			onLoggedIn = { showCoopLogin = false; navFocus.tryRequestFocus() }
		)

	removePsnAccount?.let { rid ->
		PsAlertDialog(
			onDismissRequest = { removePsnAccount = null },
			title = { Text(stringResource(R.string.psn_remove_account)) },
			text = { Text(stringResource(R.string.psn_remove_account_confirm, rid)) },
			confirmButton = {
				TextButton(onClick = {
					viewModel.removeAccount(rid)
					removePsnAccount = null
					FocusMemory.set("settings_row_${SettingsCategory.PSN.ordinal}", 1)
					navFocus.tryRequestFocus()
				}) { Text(stringResource(R.string.psn_remove_account)) }
			},
			dismissButton = {
				TextButton(onClick = { removePsnAccount = null }) { Text(stringResource(R.string.cancel)) }
			}
		)
	}
	if(showPinEdit)
	{
		val save: () -> Unit = {
			viewModel.loginPin = pinDraft
			showPinEdit = false
		}
		PsAlertDialog(
			onDismissRequest = { showPinEdit = false },
			title = { Text(stringResource(R.string.login_pin)) },
			text = {
				PinTextField(stringResource(R.string.login_pin), pinDraft,
					onValueChange = { pinDraft = it }, onDone = { save() })
			},
			confirmButton = {
				TextButton(onClick = { save() }) { Text(stringResource(R.string.save), color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = { showPinEdit = false }) { Text(stringResource(R.string.cancel), color = OnSurfaceMuted) }
			}
		)
	}
}
