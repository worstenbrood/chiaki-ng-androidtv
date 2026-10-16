// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.session

import com.metallic.chiaki.lib.CreateError
import com.metallic.chiaki.lib.QuitReason

sealed class StreamState
object StreamStateIdle: StreamState()
object StreamStateConnecting: StreamState()
object StreamStateConnected: StreamState()
object StreamStateDisconnecting: StreamState()
data class StreamStateCreateError(val error: CreateError): StreamState()
data class StreamStateQuit(val reason: QuitReason, val reasonString: String?): StreamState()
data class StreamStateLoginPinRequest(val pinIncorrect: Boolean, val requestId: Long = 0L): StreamState()
data class StreamStateRemoteProgress(val finished: Boolean): StreamState()
