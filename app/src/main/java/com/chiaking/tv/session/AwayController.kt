// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chiaking.tv.common.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Away timer for a live stream while the app is backgrounded: after [AWAY_TIMEOUT_MS] either
 * puts the console to sleep ([onSleepDue]) or raises [sleepPrompt] for the user to decide.
 */
class AwayController(private val scope: CoroutineScope, private val preferences: Preferences)
{
	companion object
	{
		const val AWAY_TIMEOUT_MS = 60000L
	}

	var sleepPrompt by mutableStateOf(false)
		private set

	private var job: Job? = null

	fun arm(onSleepDue: () -> Unit)
	{
		cancel()
		job = scope.launch {
			delay(AWAY_TIMEOUT_MS)
			job = null
			if(preferences.sleepConsoleOnExit == Preferences.SleepConsoleOnExit.ALWAYS)
				onSleepDue()
			else
				sleepPrompt = true
		}
	}

	fun cancel()
	{
		job?.cancel()
		job = null
	}

	fun dismissPrompt()
	{
		sleepPrompt = false
	}
}
