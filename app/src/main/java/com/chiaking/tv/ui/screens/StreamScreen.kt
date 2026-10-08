// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.viewinterop.AndroidView
import com.metallic.chiaki.lib.ConnectInfo
import com.chiaking.tv.session.*
import com.chiaking.tv.ui.TvButton
import com.chiaking.tv.ui.theme.*

@Composable
fun StreamScreen(
	session: StreamSession,
	onQuit: () -> Unit
)
{
	val state by session.state.collectAsState()
	var pinDialog by remember { mutableStateOf<StreamStateLoginPinRequest?>(null) }
	var quitDialog by remember { mutableStateOf<StreamStateQuit?>(null) }
	var createError by remember { mutableStateOf<StreamStateCreateError?>(null) }

	BackHandler(onBack = onQuit)

	LaunchedEffect(state) {
		when(state)
		{
			is StreamStateLoginPinRequest -> pinDialog = state as StreamStateLoginPinRequest
			is StreamStateQuit -> {
				val quit = state as StreamStateQuit
				if(quit.reason.isError)
					quitDialog = quit
				else
					onQuit()
			}
			is StreamStateCreateError -> createError = state as StreamStateCreateError
			else -> {}
		}
	}

	Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
		AndroidView(
			factory = { context ->
				SurfaceView(context).also {
					session.attachToSurfaceView(it)
				}
			},
			modifier = Modifier.fillMaxSize()
		)

		when(state)
		{
			StreamStateConnecting, StreamStateIdle -> Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center
			) {
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					CircularProgressIndicator(color = PsBlueLight)
					Spacer(Modifier.height(16.dp))
					Text("Connecting...", color = Color.White, fontSize = 20.sp)
				}
			}
			else -> {}
		}
	}

	if(pinDialog != null)
	{
		var pin by remember { mutableStateOf("") }
		AlertDialog(
			onDismissRequest = onQuit,
			title = { Text(if(pinDialog!!.pinIncorrect) "PIN Incorrect" else "Enter PIN") },
			text = {
				Column {
					Text("Enter the PIN shown on your TV/console login screen.")
					Spacer(Modifier.height(12.dp))
					TvTextField("PIN", pin) {
						if(it.all { c -> c.isDigit() } && it.length <= 8) pin = it
					}
				}
			},
			confirmButton = {
				TextButton(onClick = {
					session.setLoginPin(pin)
					pinDialog = null
				}) { Text("Connect", color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = onQuit) { Text("Quit", color = OnSurfaceMuted) }
			}
		)
	}

	if(quitDialog != null)
	{
		AlertDialog(
			onDismissRequest = onQuit,
			title = { Text("Session Ended") },
			text = {
				Text(
					quitDialog!!.reason.toString() +
						(quitDialog!!.reasonString?.let { "\n$it" } ?: ""),
					color = OnSurfaceMuted
				)
			},
			confirmButton = {
				TextButton(onClick = {
					quitDialog = null
					session.resume()
				}) { Text("Reconnect", color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = onQuit) { Text("Quit", color = OnSurfaceMuted) }
			}
		)
	}

	if(createError != null)
	{
		AlertDialog(
			onDismissRequest = onQuit,
			title = { Text("Failed to Start Session") },
			text = { Text(createError!!.error.errorCode.toString(), color = OnSurfaceMuted) },
			confirmButton = { TextButton(onClick = onQuit) { Text("Quit", color = OnSurfaceMuted) } }
		)
	}
}
