// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.stream

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import android.widget.Toast
import com.chiaking.tv.common.psn.PsnAccountRepository
import com.chiaking.tv.session.CoopNoticeKind
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.R
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.session.AwayController
import com.chiaking.tv.ui.screens.stream.RumbleFeedback
import com.chiaking.tv.session.StreamSession
import com.chiaking.tv.ui.components.dialogs.PsAlertDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@Composable
internal fun StreamRoute(
	session: StreamSession,
	preferences: Preferences,
	database: AppDatabase,
	awayController: AwayController,
	onPickerChanged: (CoopAccountPickerController?) -> Unit,
	onQuit: () -> Unit,
	onSleep: () -> Unit,
	onKeepPlaying: () -> Unit,
	onSleepAway: () -> Unit
)
{
	val scope = rememberCoroutineScope()
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val picker = remember(session) {
		CoopAccountPickerController(session.coopPlayers, session.input, PsnAccountRepository.get(context), scope)
	}
	DisposableEffect(session, lifecycleOwner) {
		session.input.observe(lifecycleOwner)
		onPickerChanged(picker)
		onDispose {
			session.input.unobserve(lifecycleOwner)
			onPickerChanged(null)
		}
	}
	LaunchedEffect(session, lifecycleOwner) {
		lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
			session.coopPlayers.notices.collect { notice ->
				val message = when(notice.kind) {
					CoopNoticeKind.JOINED -> R.string.coop_joined
					CoopNoticeKind.REFUSED -> R.string.coop_refused
					CoopNoticeKind.FULL -> R.string.coop_full
					CoopNoticeKind.PASSCODE -> R.string.coop_passcode
					CoopNoticeKind.PROFILE_REQUIRED -> R.string.coop_profile_required
					CoopNoticeKind.TIMEOUT -> R.string.coop_timeout
					CoopNoticeKind.PS5_ONLY -> R.string.coop_ps5_only
				}
				Toast.makeText(context, context.getString(message, notice.slot + 1), Toast.LENGTH_LONG).show()
			}
		}
	}
	RumbleFeedback(session, preferences.rumbleEnabled)
	StreamScreen(session, preferences, picker,
		onQuit = onQuit,
		onSleep = onSleep,
		onPinStored = { pin ->
			val hostId = session.loginPinHostId
			if(hostId != null)
				scope.launch { database.registeredHostDao().setPin(hostId, pin) }
			else
				preferences.loginPin = pin
		})
	if(awayController.sleepPrompt)
		PsAlertDialog(
			onDismissRequest = onKeepPlaying,
			title = { Text(stringResource(R.string.console_still_awake)) },
			text = { Text(stringResource(R.string.away_sleep_question)) },
			confirmButton = {
				TextButton(onClick = {
					awayController.dismissPrompt()
					onSleepAway()
				}) { Text(stringResource(R.string.sleep_console)) }
			},
			dismissButton = {
				TextButton(onClick = onKeepPlaying) { Text(stringResource(R.string.keep_playing)) }
			}
		)
}
