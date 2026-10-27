// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.stream

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chiaking.tv.R
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.session.StreamSession
import com.chiaking.tv.session.StreamStateConnected
import com.chiaking.tv.session.StreamStateConnecting
import com.chiaking.tv.session.StreamStateCreateError
import com.chiaking.tv.session.StreamStateDisconnecting
import com.chiaking.tv.session.StreamStateIdle
import com.chiaking.tv.session.StreamStateLoginPinRequest
import com.chiaking.tv.session.StreamStateQuit
import com.chiaking.tv.session.StreamStateRemoteProgress
import com.chiaking.tv.ui.components.PinTextField
import com.chiaking.tv.ui.components.dialogs.PsAlertDialog
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.PsBlueLight
import com.chiaking.tv.ui.theme.VideoBlack
import kotlinx.coroutines.delay

private const val RECONNECT_RESUME_DELAY_MS = 1500L
private const val FIRST_FRAME_POLL_MS = 100L

@Composable
fun StreamScreen(
	session: StreamSession,
	preferences: Preferences,
	onQuit: () -> Unit,
	onSleep: () -> Unit,
	onPinStored: (String) -> Unit = {}
)
{
	val state by session.state.collectAsState()
	val hudVisible by session.hudVisible.collectAsState()
	val coopChoice by session.coopAccountChoice.collectAsState()
	LaunchedEffect(coopChoice?.id) { if(coopChoice != null) session.hideHud() }
	val disconnectDialogVisible by session.disconnectDialogVisible.collectAsState()
	var pinDialog by remember { mutableStateOf<StreamStateLoginPinRequest?>(null) }
	var quitDialog by remember { mutableStateOf<StreamStateQuit?>(null) }
	var createError by remember { mutableStateOf<StreamStateCreateError?>(null) }
	var firstFrameSeen by remember(session) { mutableStateOf(false) }

	val closeAction: () -> Unit = {
		when(preferences.disconnectAction)
		{
			Preferences.DisconnectAction.SLEEP -> { session.gotoBed(); onSleep() }
			Preferences.DisconnectAction.NOTHING -> onQuit()
			Preferences.DisconnectAction.ASK -> session.requestDisconnectDialog()
		}
	}

	BackHandler(onBack = onQuit)

	LaunchedEffect(state) {
		when(state)
		{
			is StreamStateLoginPinRequest -> {
				val pinReq = state as StreamStateLoginPinRequest
				val stored = session.storedLoginPin?.takeIf { it.isNotEmpty() } ?: preferences.loginPin
				// Auto-submit the stored PIN; only prompt when unset or the console rejected it
				if(!pinReq.pinIncorrect && stored.isNotEmpty())
					session.setLoginPin(stored)
				else
					pinDialog = pinReq
			}
			is StreamStateQuit -> {
				val quit = state as StreamStateQuit
				if(session.disconnecting)
					{}
				else if(quit.reason.isError)
					quitDialog = quit
				else
					onQuit()
			}
			is StreamStateCreateError -> createError = state as StreamStateCreateError
			StreamStateConnecting -> firstFrameSeen = false
			else -> {}
		}
	}

	LaunchedEffect(state, session) {
		if(state is StreamStateConnected && !firstFrameSeen)
		{
			while(!firstFrameSeen && !session.disconnecting)
			{
				val stats = session.getStats()
				if(stats != null && stats[0] > 0L)
					firstFrameSeen = true
				else
					delay(FIRST_FRAME_POLL_MS)
			}
		}
	}

	var displayMode by remember { mutableStateOf(preferences.displayMode) }

	Box(modifier = Modifier.fillMaxSize().background(VideoBlack)) {
		BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
			val vprofile = session.connectInfo.videoProfile
			val vAspect = if(vprofile.height > 0) vprofile.width.toFloat() / vprofile.height else 16f / 9f
			val sAspect = maxWidth / maxHeight
			// The video buffer always stretches to fill the SurfaceView bounds, so the
			// display mode is implemented purely by sizing the view (like VideoView does).
			val (vw, vh) = when(displayMode)
			{
				Preferences.DisplayMode.FIT ->
					if(vAspect > sAspect) maxWidth to (maxWidth / vAspect)
					else (maxHeight * vAspect) to maxHeight
				Preferences.DisplayMode.ZOOM ->
					if(vAspect > sAspect) (maxHeight * vAspect) to maxHeight
					else maxWidth to (maxWidth / vAspect)
				Preferences.DisplayMode.STRETCH -> maxWidth to maxHeight
			}
			AndroidView(
				factory = { context ->
					SurfaceView(context).also {
						session.attachToSurfaceView(it)
					}
				},
				modifier = Modifier
					.size(vw, vh)
					.align(Alignment.Center)
			)
		}

		when(state)
		{
			StreamStateConnecting, StreamStateIdle -> HudSpinner(stringResource(R.string.connecting))
			// While the stored PIN is auto-submitted there is no dialog — keep the spinner up
			is StreamStateLoginPinRequest -> if(pinDialog == null) HudSpinner(stringResource(R.string.connecting))
			is StreamStateRemoteProgress -> HudSpinner(stringResource(
				if((state as StreamStateRemoteProgress).finished) R.string.remote_data_connected
				else R.string.remote_data_connecting
			))
			// Keep the spinner until the decoder has actually received a frame (black screen otherwise)
			StreamStateConnected -> if(!firstFrameSeen) HudSpinner(stringResource(R.string.connecting))
			StreamStateDisconnecting -> HudSpinner(stringResource(R.string.disconnecting))
			else -> {}
		}

		StreamHud(
			session = session,
			preferences = preferences,
			hudVisible = hudVisible && coopChoice == null,
			state = state,
			displayMode = displayMode,
			onCycleDisplayMode = {
				val values = Preferences.DisplayMode.values()
				displayMode = values[(displayMode.ordinal + 1) % values.size]
				preferences.displayMode = displayMode
			},
			onClose = closeAction,
			modifier = Modifier.align(Alignment.BottomCenter)
		)
		coopChoice?.let { CoopAccountPicker(it, session) }
	}

	if(pinDialog != null)
	{
		val pinReq = pinDialog!!
		var pin by remember(pinReq.requestId) {
			mutableStateOf(session.storedLoginPin?.takeIf { it.isNotEmpty() } ?: preferences.loginPin)
		}
		val submit: () -> Unit = {
			onPinStored(pin)
			session.setLoginPin(pin)
			pinDialog = null
		}
		PsAlertDialog(
			onDismissRequest = onQuit,
			title = { Text(stringResource(if(pinReq.pinIncorrect) R.string.pin_incorrect else R.string.enter_pin)) },
			text = {
				Column {
					Text(stringResource(R.string.pin_instructions))
					Spacer(Modifier.height(12.dp))
					PinTextField(stringResource(R.string.pin), pin,
						onValueChange = { pin = it }, onDone = { submit() })
				}
			},
			confirmButton = {
				TextButton(onClick = { submit() }) { Text(stringResource(R.string.connect), color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = onQuit) { Text(stringResource(R.string.quit), color = OnSurfaceMuted) }
			}
		)
	}

	if(quitDialog != null)
	{
		PsAlertDialog(
			onDismissRequest = onQuit,
			title = { Text(stringResource(R.string.session_ended)) },
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
					session.shutdown(StreamStateIdle)
					session.resumeDelayed(RECONNECT_RESUME_DELAY_MS)
				}) { Text(stringResource(R.string.reconnect), color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = onQuit) { Text(stringResource(R.string.quit), color = OnSurfaceMuted) }
			}
		)
	}

	if(disconnectDialogVisible)
	{
		PsAlertDialog(
			onDismissRequest = { session.dismissDisconnectDialog() },
			title = { Text(stringResource(R.string.disconnect_title)) },
			text = { Text(stringResource(R.string.disconnect_sleep_question)) },
			confirmButton = {
				TextButton(onClick = {
					session.dismissDisconnectDialog()
					session.gotoBed()
					onSleep()
				}) { Text(stringResource(R.string.sleep), color = PsBlueLight) }
			},
			dismissButton = {
				TextButton(onClick = {
					session.dismissDisconnectDialog()
					onQuit()
				}) { Text(stringResource(R.string.disconnect), color = OnSurfaceMuted) }
			}
		)
	}

	if(createError != null)
	{
		PsAlertDialog(
			onDismissRequest = onQuit,
			title = { Text(stringResource(R.string.failed_to_start_session)) },
			text = { Text(createError!!.error.errorCode.toString(), color = OnSurfaceMuted) },
			confirmButton = { TextButton(onClick = onQuit) { Text(stringResource(R.string.quit), color = OnSurfaceMuted) } }
		)
	}
}
