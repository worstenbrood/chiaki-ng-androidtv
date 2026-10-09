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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.common.LogFile
import com.chiaking.tv.common.LogManager
import com.chiaking.tv.common.RegisteredHost
import com.chiaking.tv.ui.ScreenHeader
import com.chiaking.tv.ui.SectionHeader
import com.chiaking.tv.ui.SettingsCard
import com.chiaking.tv.ui.TvActionRow
import com.chiaking.tv.ui.TvButton
import com.chiaking.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LogsScreen(logManager: LogManager, onBack: () -> Unit)
{
	var selected by remember { mutableStateOf<LogFile?>(null) }
	val files = remember { logManager.files }

	if(selected != null)
	{
		val content = remember { mutableStateOf("") }
		LaunchedEffect(selected) {
			content.value = withContext(Dispatchers.IO) {
				try { selected!!.file.readText() } catch(e: Exception) { "Failed to read log" }
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

	Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
		ScreenHeader("Session Logs", onBack, fontSize = 28.sp)
		SectionHeader("Recorded Sessions")
		SettingsCard {
			if(files.isEmpty())
				Text("No logs recorded yet.", modifier = Modifier.padding(20.dp), color = OnSurfaceMuted)
			files.forEach { file ->
				TvActionRow(file.date.toString(), null) { selected = file }
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
	Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
		ScreenHeader("Registered Consoles", onBack, fontSize = 28.sp)
		SectionHeader("Registered")
		SettingsCard {
			if(registeredHosts.isEmpty())
				Text("No consoles registered.", modifier = Modifier.padding(20.dp), color = OnSurfaceMuted)
			registeredHosts.forEach { host ->
				TvActionRow(
					host.serverNickname ?: host.serverMac.toString(),
					"${host.serverMac} / ${if(host.target.isPS5) "PS5" else "PS4"}"
				) { onDeleteRegistered(host) }
			}
		}
	}
}
