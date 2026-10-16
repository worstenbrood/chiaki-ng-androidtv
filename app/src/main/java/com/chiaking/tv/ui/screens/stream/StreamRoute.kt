// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.stream

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.chiaking.tv.R
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.session.AwayController
import com.chiaking.tv.session.RumbleFeedback
import com.chiaking.tv.session.StreamSession
import com.chiaking.tv.ui.components.dialogs.PsAlertDialog
import kotlinx.coroutines.launch

@Composable
fun StreamRoute(
	session: StreamSession,
	preferences: Preferences,
	database: AppDatabase,
	awayController: AwayController,
	onQuit: () -> Unit,
	onSleep: () -> Unit,
	onKeepPlaying: () -> Unit,
	onSleepAway: () -> Unit
)
{
	val scope = rememberCoroutineScope()
	RumbleFeedback(session, preferences.rumbleEnabled)
	StreamScreen(session, preferences,
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
