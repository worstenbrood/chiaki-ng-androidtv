// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.R
import com.metallic.chiaki.lib.RegistInfo
import com.metallic.chiaki.lib.Target
import com.chiaking.tv.regist.RegistExecuteViewModel
import com.chiaking.tv.ui.*
import com.chiaking.tv.ui.theme.*

enum class ConsoleVersion { PS5, PS4_GE_8, PS4_GE_7, PS4_LT_7 }

@Composable
fun RegistScreen(
	initialHost: String?,
	initialPs5: Boolean? = null,
	onBack: () -> Unit,
	onStartRegist: (RegistInfo, Long?) -> Unit
)
{
	var host by remember { mutableStateOf(initialHost ?: "255.255.255.255") }
	var version by remember { mutableStateOf(
		when(initialPs5)
		{
			true -> ConsoleVersion.PS5
			false -> ConsoleVersion.PS4_GE_8
			null -> ConsoleVersion.PS5
		}
	) }
	var psnId by remember { mutableStateOf("") }
	var pin by remember { mutableStateOf("") }
	var error by remember { mutableStateOf<String?>(null) }

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		ScreenHeader(stringResource(R.string.register_console), onBack, modifier = Modifier.fillMaxWidth())

		Spacer(Modifier.height(24.dp))

		SettingsCard {
			Column(modifier = Modifier.padding(vertical = 12.dp)) {
				Text(stringResource(R.string.console_version), fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
				Row(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
				) {
					ConsoleVersion.entries.forEach { v ->
						TvButton(
							text = stringResource(
								when(v)
								{
									ConsoleVersion.PS5 -> R.string.ps5
									ConsoleVersion.PS4_GE_8 -> R.string.ps4_ge_900
									ConsoleVersion.PS4_GE_7 -> R.string.ps4_ge_700
									ConsoleVersion.PS4_LT_7 -> R.string.ps4_lt_700
								}
							),
							onClick = { version = v },
							colors = if(version == v)
								ButtonDefaults.buttonColors(containerColor = PsBlue, contentColor = OnPrimary)
							else
								ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = OnSurfaceLight)
						)
					}
				}

				TvTextField(stringResource(R.string.host_address), host) { host = it }
				TvTextField(
					stringResource(if(version == ConsoleVersion.PS4_LT_7) R.string.psn_online_id else R.string.psn_account_id),
					psnId
				) { psnId = it }
				TvTextField(
					stringResource(R.string.regist_pin_hint), pin,
					textStyle = androidx.compose.ui.text.TextStyle(
						fontSize = 26.sp,
						letterSpacing = 8.sp,
						fontWeight = FontWeight.Bold
					)
				) {
					if(it.length <= 8 && it.all { c -> c.isDigit() }) pin = it
				}
			}
		}

		Spacer(Modifier.height(16.dp))

		Text(
			stringResource(if(version == ConsoleVersion.PS5) R.string.regist_help_ps5 else R.string.regist_help_ps4),
			color = OnSurfaceMuted,
			fontSize = 14.sp,
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(horizontal = 32.dp)
		)

		Spacer(Modifier.height(20.dp))

		if(error != null)
			Text(error!!, color = ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))

	val fillInMsg = stringResource(R.string.regist_fill_in)
	val accountIdInvalidMsg = stringResource(R.string.regist_account_id_invalid)
	PsButton(
			text = stringResource(R.string.register),
			onClick = {
				val hostValid = host.isNotEmpty()
				val pinValid = pin.length == 8
				val psnValid = psnId.isNotEmpty()
				if(!hostValid || !pinValid || !psnValid)
				{
					error = fillInMsg
					return@PsButton
				}
				error = null
				val target = when(version)
				{
					ConsoleVersion.PS5 -> Target.PS5_1
					ConsoleVersion.PS4_GE_8 -> Target.PS4_10
					ConsoleVersion.PS4_GE_7 -> Target.PS4_9
					ConsoleVersion.PS4_LT_7 -> Target.PS4_8
				}
				val psnAccountId: ByteArray? = if(version != ConsoleVersion.PS4_LT_7)
					try { android.util.Base64.decode(psnId, android.util.Base64.DEFAULT) } catch(e: IllegalArgumentException) { null }
				else null
				val psnOnlineId: String? = if(version == ConsoleVersion.PS4_LT_7) psnId else null
				if(version != ConsoleVersion.PS4_LT_7 && (psnAccountId == null || psnAccountId.size != RegistInfo.ACCOUNT_ID_SIZE))
				{
					error = accountIdInvalidMsg
					return@PsButton
				}
				onStartRegist(RegistInfo(target, host, host == "255.255.255.255", psnOnlineId, psnAccountId, pin.toInt()), null)
			}
		)
	}
}

@Composable
fun TvTextField(
	label: String,
	value: String,
	enabled: Boolean = true,
	imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Default,
	keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
	textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
	onValueChange: (String) -> Unit
)
{
	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
		enabled = enabled,
		label = { Text(label) },
		textStyle = textStyle,
		keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = imeAction),
		keyboardActions = keyboardActions,
		singleLine = true,
		colors = OutlinedTextFieldDefaults.colors(
			focusedBorderColor = PsBlueLight,
			focusedLabelColor = PsBlueLight
		),
		modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
	)
}

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
