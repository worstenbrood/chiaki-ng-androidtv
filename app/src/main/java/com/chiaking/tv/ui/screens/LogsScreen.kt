// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.R
import com.chiaking.tv.common.LogFile
import com.chiaking.tv.common.LogManager
import com.chiaking.tv.common.RegisteredHost
import com.chiaking.tv.ui.FocusMemory
import com.chiaking.tv.ui.ScreenHeader
import com.chiaking.tv.ui.SectionHeader
import com.chiaking.tv.ui.SettingsCard
import com.chiaking.tv.ui.TvActionRow
import com.chiaking.tv.ui.TvButton
import com.chiaking.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val FOCUS_RESTORE_DELAY_MS = 100L

@Composable
fun LogsScreen(logManager: LogManager, onBack: () -> Unit)
{
	var selected by remember { mutableStateOf<LogFile?>(null) }
	val files = remember { logManager.files }

	if(selected != null)
	{
		val content = remember { mutableStateOf("") }
		val readFailed = stringResource(R.string.failed_to_read_log)
		LaunchedEffect(selected) {
			content.value = withContext(Dispatchers.IO) {
				try { selected!!.file.readText() } catch(e: Exception) { readFailed }
			}
		}
		Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
			ScreenHeader(selected!!.filename, { selected = null }, fontSize = 18.sp)
			Spacer(Modifier.height(12.dp))
			Text(
				content.value,
				fontSize = 11.sp,
				color = OnSurfaceMuted,
				modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
			)
		}
		return
	}

	val restore = remember { FocusMemory.get("logs") }
	val rowFocus = remember { FocusRequester() }
	val backFocus = remember { FocusRequester() }
	LaunchedEffect(Unit) {
		delay(FOCUS_RESTORE_DELAY_MS)
		try { if(restore == 0) backFocus.requestFocus() else rowFocus.requestFocus() } catch(e: Exception) { }
	}

	Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
		ScreenHeader(
			stringResource(R.string.session_logs), onBack, fontSize = 28.sp,
			backFocusRequester = if(restore == 0) backFocus else null,
			onBackFocus = { if(it) FocusMemory.set("logs", 0) }
		)
		SectionHeader(stringResource(R.string.recorded_sessions))
		SettingsCard {
			if(files.isEmpty())
				Text(stringResource(R.string.no_logs), modifier = Modifier.padding(20.dp), color = OnSurfaceMuted)
			files.forEachIndexed { index, file ->
				TvActionRow(
					file.date.toString(), null,
					onClick = { selected = file },
					focusRequester = if(index + 1 == restore) rowFocus else null,
					onFocus = { if(it) FocusMemory.set("logs", index + 1) }
				)
			}
		}
	}
}

@Composable
fun ManageHostsScreen(
	registeredHosts: List<RegisteredHost>,
	onDeleteRegistered: (RegisteredHost) -> Unit,
	onBack: () -> Unit
)
{
	val restore = remember { FocusMemory.get("hosts") }
	val rowFocus = remember { FocusRequester() }
	val backFocus = remember { FocusRequester() }
	LaunchedEffect(Unit) {
		delay(FOCUS_RESTORE_DELAY_MS)
		try { if(restore == 0) backFocus.requestFocus() else rowFocus.requestFocus() } catch(e: Exception) { }
	}

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
					onClick = { onDeleteRegistered(host) },
					focusRequester = if(index + 1 == restore) rowFocus else null,
					onFocus = { if(it) FocusMemory.set("hosts", index + 1) }
				)
			}
		}
	}
}
