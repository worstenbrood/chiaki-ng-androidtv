// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

const val FOCUS_RESTORE_DELAY_MS = 100L

/** Requests focus, swallowing the failure when the requester is not attached (yet). */
fun FocusRequester?.tryRequestFocus()
{
	try { this?.requestFocus() } catch(e: Exception) { }
}

/**
 * Restores the D-pad cursor shortly after a screen appears: index 0 focuses [zeroFocus]
 * (usually the Back button), anything else [otherFocus], falling back to [zeroFocus]
 * when the row is not focusable yet.
 */
@Composable
fun rememberFocusRestore(
	target: Int,
	zeroFocus: FocusRequester,
	otherFocus: FocusRequester,
	delayMs: Long = FOCUS_RESTORE_DELAY_MS
)
{
	LaunchedEffect(Unit)
	{
		delay(delayMs)
		if(target == 0)
			zeroFocus.tryRequestFocus()
		else
			try { otherFocus.requestFocus() } catch(e: Exception) { zeroFocus.tryRequestFocus() }
	}
}
