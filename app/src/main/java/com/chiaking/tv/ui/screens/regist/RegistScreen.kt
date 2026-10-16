// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.regist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chiaking.tv.R
import com.chiaking.tv.ui.components.buttons.PsButton
import com.chiaking.tv.ui.components.ScreenHeader
import com.chiaking.tv.ui.components.settings.SettingsCard
import com.chiaking.tv.ui.components.buttons.TvButton
import com.chiaking.tv.ui.components.TvTextField
import com.chiaking.tv.ui.theme.ErrorRed
import com.chiaking.tv.ui.theme.OnPrimary
import com.chiaking.tv.ui.theme.OnSurfaceLight
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.PsBlue
import com.chiaking.tv.ui.theme.SurfaceDark
import com.metallic.chiaki.lib.RegistInfo
import com.metallic.chiaki.lib.Target

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
					textStyle = TextStyle(
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
