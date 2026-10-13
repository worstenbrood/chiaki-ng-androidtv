// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.session

import android.content.Context
import com.chiaking.tv.common.AudioDevices
import com.chiaking.tv.common.DisplayHost
import com.chiaking.tv.common.LogManager
import com.chiaking.tv.common.Preferences
import com.metallic.chiaki.lib.ConnectInfo

/**
 * Process-wide owner of the active [StreamSession]. Survives Activity recreation and backgrounding,
 * so a session can be kept alive (and its surface re-attached) instead of disconnect + reconnect.
 */
object StreamManager
{
	var session: StreamSession? = null
		private set

	var displayHost: DisplayHost? = null
		private set

	var input: StreamInput? = null
		private set

	fun start(context: Context, preferences: Preferences, logManager: LogManager, host: DisplayHost): StreamSession?
	{
		if(session != null)
			return session
		val registeredHost = host.registeredHost ?: return null
		val connectInfo = ConnectInfo(
			host.isPS5,
			host.host,
			registeredHost.rpRegistKey,
			registeredHost.rpKey,
			preferences.videoProfile
		)
		val input = StreamInput(context.applicationContext, preferences)
		val session = StreamSession(connectInfo, logManager, preferences.logVerbose, input)
		this.input = input
		this.displayHost = host
		this.session = session
		session.resume()
		session.applyAudioSettings(preferences.audioVolume,
			AudioDevices.resolveDeviceId(context, preferences.audioDeviceName, preferences.audioDeviceId),
			preferences.audioBufferMs)
		return session
	}

	fun clear(session: StreamSession? = null)
	{
		if(session == null || this.session === session)
		{
			this.session = null
			displayHost = null
			input = null
		}
	}
}
