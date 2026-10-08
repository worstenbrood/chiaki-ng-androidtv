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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metallic.chiaki.lib.RegistInfo
import com.metallic.chiaki.lib.Target
import com.chiaking.tv.regist.RegistExecuteViewModel
import com.chiaking.tv.ui.*
import com.chiaking.tv.ui.theme.*

enum class ConsoleVersion { PS5, PS4_GE_8, PS4_GE_7, PS4_LT_7 }

@Composable
fun RegistScreen(
	initialHost: String?,
	onBack: () -> Unit,
	onStartRegist: (RegistInfo, Long?) -> Unit
)
{
	var host by remember { mutableStateOf(initialHost ?: "255.255.255.255") }
	var version by remember { mutableStateOf(ConsoleVersion.PS5) }
	var psnId by remember { mutableStateOf("") }
	var pin by remember { mutableStateOf("") }
	var error by remember { mutableStateOf<String?>(null) }

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			TvButton("Back", onClick = onBack)
			Spacer(Modifier.width(20.dp))
			Text("Register Console", fontSize = 30.sp, fontWeight = FontWeight.Bold)
		}

		Spacer(Modifier.height(24.dp))

		SettingsCard {
			Column(modifier = Modifier.padding(vertical = 12.dp)) {
				Text("Console Version", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
				Row(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
				) {
					ConsoleVersion.entries.forEach { v ->
						TvButton(
							text = when(v)
							{
								ConsoleVersion.PS5 -> "PS5"
								ConsoleVersion.PS4_GE_8 -> "PS4 (9.00+)"
								ConsoleVersion.PS4_GE_7 -> "PS4 (7.0+)"
								ConsoleVersion.PS4_LT_7 -> "PS4 (<7.0)"
							},
							onClick = { version = v },
							colors = if(version == v)
								ButtonDefaults.buttonColors(containerColor = PsBlue, contentColor = Color.White)
							else
								ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = OnSurfaceLight)
						)
					}
				}

				TvTextField("Host Address", host) { host = it }
				TvTextField(
					if(version == ConsoleVersion.PS4_LT_7) "PSN Online ID" else "PSN Account ID (base64)",
					psnId
				) { psnId = it }
				TvTextField("PIN (8 digits, shown on pairing screen)", pin) {
					if(it.length <= 8 && it.all { c -> c.isDigit() }) pin = it
				}
			}
		}

		Spacer(Modifier.height(16.dp))

		Text(
			when(version)
			{
				ConsoleVersion.PS5 -> "On your PS5: Settings > System > Remote Play > Link Device. The 8-digit PIN is shown there."
				else -> "On your console: Settings > Remote Play Connection Settings > Add Device. The 8-digit PIN is shown there."
			},
			color = OnSurfaceMuted,
			fontSize = 14.sp,
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(horizontal = 32.dp)
		)

		Spacer(Modifier.height(20.dp))

		if(error != null)
			Text(error!!, color = ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))

		TvButton(
			text = "Register",
			onClick = {
				val hostValid = host.isNotEmpty()
				val pinValid = pin.length == 8
				val psnValid = psnId.isNotEmpty()
				if(!hostValid || !pinValid || !psnValid)
				{
					error = "Fill in host, PSN ID and the 8-digit PIN."
					return@TvButton
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
					error = "PSN Account ID must be a 44-character base64 string (8 bytes)."
					return@TvButton
				}
				onStartRegist(RegistInfo(target, host, host == "255.255.255.255", psnOnlineId, psnAccountId, pin.toInt()), null)
			},
			colors = ButtonDefaults.buttonColors(containerColor = PsBlue, contentColor = Color.White)
		)
	}
}

@Composable
fun TvTextField(label: String, value: String, onValueChange: (String) -> Unit)
{
	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
		label = { Text(label) },
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
		Text("Registering Console", fontSize = 30.sp, fontWeight = FontWeight.Bold)
		Spacer(Modifier.height(24.dp))

		val (text, color) = when(state)
		{
			RegistExecuteViewModel.State.RUNNING -> "Registering..." to PsBlueLight
			RegistExecuteViewModel.State.SUCCESSFUL -> "Console registered successfully!" to SuccessGreen
			RegistExecuteViewModel.State.SUCCESSFUL_DUPLICATE -> "Console was already registered (updated)." to WarningAmber
			RegistExecuteViewModel.State.FAILED -> "Registration failed." to ErrorRed
			RegistExecuteViewModel.State.STOPPED -> "Registration cancelled." to OnSurfaceMuted
			RegistExecuteViewModel.State.IDLE -> "Starting..." to OnSurfaceMuted
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
			TvButton(
				text = "Done",
				onClick = onDone,
				colors = ButtonDefaults.buttonColors(containerColor = PsBlue, contentColor = Color.White)
			)
		}
	}
}
