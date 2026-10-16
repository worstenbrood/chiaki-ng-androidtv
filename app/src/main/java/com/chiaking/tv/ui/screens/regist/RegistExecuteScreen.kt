// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.regist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.R
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.regist.RegistExecuteViewModel
import com.chiaking.tv.ui.components.buttons.PsButton
import com.chiaking.tv.ui.theme.ErrorRed
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.PsBlueLight
import com.chiaking.tv.ui.theme.SuccessGreen
import com.chiaking.tv.ui.theme.WarningAmber
import com.metallic.chiaki.lib.RegistInfo

@Composable
fun RegistExecuteScreen(viewModel: RegistExecuteViewModel, onDone: () -> Unit)
{
	val state by viewModel.state.collectAsState()
	val logText by viewModel.logText.collectAsState()

	Column(
		modifier = Modifier.fillMaxSize().padding(32.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Text(stringResource(R.string.registering_console), fontSize = 30.sp, fontWeight = FontWeight.Bold)
		Spacer(Modifier.height(24.dp))

		val (text, color) = when(state)
		{
			RegistExecuteViewModel.State.RUNNING -> stringResource(R.string.regist_running) to PsBlueLight
			RegistExecuteViewModel.State.SUCCESSFUL -> stringResource(R.string.regist_success) to SuccessGreen
			RegistExecuteViewModel.State.SUCCESSFUL_DUPLICATE -> stringResource(R.string.regist_duplicate) to WarningAmber
			RegistExecuteViewModel.State.FAILED -> stringResource(R.string.regist_failed) to ErrorRed
			RegistExecuteViewModel.State.STOPPED -> stringResource(R.string.regist_cancelled) to OnSurfaceMuted
			RegistExecuteViewModel.State.IDLE -> stringResource(R.string.regist_starting) to OnSurfaceMuted
		}
		Text(text, fontSize = 22.sp, color = color, fontWeight = FontWeight.Bold)
		Spacer(Modifier.height(16.dp))

		Text(
			logText,
			fontSize = 12.sp,
			color = OnSurfaceMuted,
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.verticalScroll(rememberScrollState())
		)

		if(state != RegistExecuteViewModel.State.RUNNING)
		{
			Spacer(Modifier.height(16.dp))
			PsButton(
				text = stringResource(R.string.done),
				onClick = onDone
			)
		}
	}
}

@Composable
fun RegistExecuteRoute(database: AppDatabase, info: RegistInfo, assignManualHostId: Long?, onDone: () -> Unit)
{
	val viewModel = remember { RegistExecuteViewModel(database) }
	LaunchedEffect(Unit) { viewModel.start(info, assignManualHostId) }
	RegistExecuteScreen(viewModel, onDone)
}
