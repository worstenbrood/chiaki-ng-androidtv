// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.logs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.chiaking.tv.common.logging.LogFile
import com.chiaking.tv.common.logging.LogManager
import com.chiaking.tv.ui.focus.FocusMemory
import com.chiaking.tv.ui.components.ScreenHeader
import com.chiaking.tv.ui.components.settings.SectionHeader
import com.chiaking.tv.ui.components.settings.SettingsCard
import com.chiaking.tv.ui.components.settings.TvActionRow
import com.chiaking.tv.ui.focus.rememberFocusRestore
import com.chiaking.tv.ui.theme.OnSurfaceMuted
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
	rememberFocusRestore(restore, backFocus, rowFocus)

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
