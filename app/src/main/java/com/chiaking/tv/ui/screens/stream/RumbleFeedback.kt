// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.stream

import com.chiaking.tv.session.StreamSession
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest

private const val RUMBLE_VIBRATE_MS = 1000L

/** Drives device vibration from the session's rumble state while the app is resumed. */
@Composable
fun RumbleFeedback(session: StreamSession, enabled: Boolean)
{
	if(!enabled)
		return
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	LaunchedEffect(session) {
		@Suppress("DEPRECATION")
		val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
		lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
			session.rumbleState.collectLatest { rumble ->
				val amplitude = (rumble.left.toInt() + rumble.right.toInt()) / 2
				vibrator.cancel()
				if(amplitude == 0) return@collectLatest
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
					vibrator.vibrate(VibrationEffect.createOneShot(RUMBLE_VIBRATE_MS, amplitude))
				else
					@Suppress("DEPRECATION") vibrator.vibrate(RUMBLE_VIBRATE_MS)
			}
		}
	}
}
