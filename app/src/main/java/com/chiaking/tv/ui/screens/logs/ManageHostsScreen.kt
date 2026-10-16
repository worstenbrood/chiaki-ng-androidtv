// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.logs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.R
import com.chiaking.tv.common.db.RegisteredHost
import com.chiaking.tv.ui.focus.FocusMemory
import com.chiaking.tv.ui.components.PinTextField
import com.chiaking.tv.ui.components.dialogs.PsAlertDialog
import com.chiaking.tv.ui.components.ScreenHeader
import com.chiaking.tv.ui.components.settings.SectionHeader
import com.chiaking.tv.ui.components.settings.SettingsCard
import com.chiaking.tv.ui.components.settings.TvActionRow
import com.chiaking.tv.ui.focus.rememberFocusRestore
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.PsBlueLight

@Composable
fun ManageHostsScreen(
	registeredHosts: List<RegisteredHost>,
	onDeleteRegistered: (RegisteredHost) -> Unit,
	onSetPin: (RegisteredHost, String?) -> Unit,
	onBack: () -> Unit
)
{
	var detailHost by remember { mutableStateOf<RegisteredHost?>(null) }
	var deleteHost by remember { mutableStateOf<RegisteredHost?>(null) }
	var pinHost by remember { mutableStateOf<RegisteredHost?>(null) }
	var pinDraft by remember { mutableStateOf("") }
	val restore = remember { FocusMemory.get("hosts") }
	val rowFocus = remember { FocusRequester() }
	val backFocus = remember { FocusRequester() }
	rememberFocusRestore(restore, backFocus, rowFocus)

	Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
		ScreenHeader(
			stringResource(R.string.registered_consoles), onBack, fontSize = 28.sp,
			backFocusRequester = if(restore == 0) backFocus else null,
			onBackFocus = { if(it) FocusMemory.set("hosts", 0) }
		)
		SectionHeader(stringResource(R.string.registered))
		SettingsCard {
			if(registeredHosts.isEmpty())
				Text(stringResource(R.string.no_consoles_registered), modifier = Modifier.padding(20.dp), color = OnSurfaceMuted)
			registeredHosts.forEachIndexed { index, host ->
				TvActionRow(
					host.serverNickname ?: host.serverMac.toString(),
					"${host.serverMac} / ${stringResource(if(host.target.isPS5) R.string.ps5 else R.string.ps4)}",
					onClick = { detailHost = host },
					focusRequester = if(index + 1 == restore) rowFocus else null,
					onFocus = { if(it) FocusMemory.set("hosts", index + 1) }
				)
			}
		}
	}

	if(detailHost != null)
	{
		val host = detailHost!!
		PsAlertDialog(
			onDismissRequest = { detailHost = null },
			title = { Text(host.serverNickname ?: host.serverMac.toString()) },
			text = {
				Text("${host.serverMac} / ${stringResource(if(host.target.isPS5) R.string.ps5 else R.string.ps4)} / " +
					"${stringResource(R.string.login_pin)}: " +
					stringResource(if(host.rpPin.isNullOrEmpty()) R.string.login_pin_not_set else R.string.login_pin_set),
					color = OnSurfaceMuted)
			},
			confirmButton = {
				TextButton(onClick = {
					pinDraft = host.rpPin ?: ""
					pinHost = host
					detailHost = null
				}) { Text(stringResource(R.string.login_pin), color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = {
					detailHost = null
					deleteHost = host
				}) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
			}
		)
	}

	if(deleteHost != null)
	{
		val host = deleteHost!!
		PsAlertDialog(
			onDismissRequest = { deleteHost = null },
			title = { Text(host.serverNickname ?: host.serverMac.toString()) },
			text = { Text(stringResource(R.string.delete_console_confirm), color = OnSurfaceMuted) },
			confirmButton = {
				TextButton(onClick = {
					deleteHost = null
					onDeleteRegistered(host)
				}) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
			},
			dismissButton = {
				TextButton(onClick = { deleteHost = null })
				{ Text(stringResource(R.string.cancel), color = PsBlueLight) }
			}
		)
	}

	if(pinHost != null)
	{
		val host = pinHost!!
		val save: () -> Unit = {
			onSetPin(host, pinDraft.ifEmpty { null })
			pinHost = null
		}
		PsAlertDialog(
			onDismissRequest = { pinHost = null },
			title = { Text(stringResource(R.string.login_pin)) },
			text = {
				PinTextField(stringResource(R.string.pin), pinDraft,
					onValueChange = { pinDraft = it }, onDone = { save() })
			},
			confirmButton = {
				TextButton(onClick = { save() }) { Text(stringResource(R.string.save), color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = { pinHost = null })
				{ Text(stringResource(R.string.cancel), color = OnSurfaceMuted) }
			}
		)
	}
}
