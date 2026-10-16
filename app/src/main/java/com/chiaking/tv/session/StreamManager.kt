// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.session

import android.content.Context
import com.chiaking.tv.common.AudioDevices
import com.chiaking.tv.common.DisplayHost
import com.chiaking.tv.common.LastSeenHosts
import com.chiaking.tv.common.LogManager
import com.chiaking.tv.common.Preferences
import com.chiaking.tv.discovery.serverMac
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.DiscoveryHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-wide owner of the active [StreamSession]. Survives Activity recreation and backgrounding,
 * so a session can be kept alive (and its surface re-attached) instead of disconnect + reconnect.
 * Also owns the console sleep ("goto bed") flows, since both need session knowledge.
 */
object StreamManager
{
	private const val TAG = "StreamManager"
	private const val SLEEP_RECONNECT_TIMEOUT_MS = 10000L
	private const val GOTO_BED_SETTLE_MS = 500L

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
			preferences.videoProfile,
			enableIDROnFECFailure = preferences.idrOnFecFailure
		)
		val input = StreamInput(context.applicationContext, preferences)
		val session = StreamSession(connectInfo, logManager, preferences.logVerbose, input)
		this.input = input
		this.displayHost = host
		this.session = session
		session.storedLoginPin = registeredHost.rpPin
		session.loginPinHostId = registeredHost.id
		session.resume()
		session.applyAudioSettings(preferences.audioVolume,
			AudioDevices.resolveDeviceId(context, preferences.audioDeviceName, preferences.audioDeviceId),
			preferences.audioBufferMs)
		return session
	}

	/**
	 * Starts a Remote Play over Internet session on a holepunch session previously created via
	 * [com.metallic.chiaki.lib.Holepunch.createSession]. Ownership of the holepunch session
	 * transfers to the native session (freed when it terminates).
	 */
	fun startRemote(context: Context, preferences: Preferences, logManager: LogManager,
		holepunchSessionPtr: Long, psnAccountId: ByteArray, ps5: Boolean,
		autoRegist: Boolean = true): StreamSession?
	{
		if(session != null)
			return session
		val connectInfo = ConnectInfo(
			ps5,
			"",
			ByteArray(16),
			ByteArray(16),
			preferences.videoProfile,
			psnAccountId,
			holepunchSessionPtr,
			autoRegist,
			enableIDROnFECFailure = preferences.idrOnFecFailure
		)
		val input = StreamInput(context.applicationContext, preferences)
		val session = StreamSession(connectInfo, logManager, preferences.logVerbose, input)
		this.input = input
		this.displayHost = null
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

	/** Marks the current display host as STANDBY in the last-seen cache (after we put it to sleep). */
	fun markDisplayHostStandby()
	{
		displayHost?.registeredHost?.serverMac?.let {
			LastSeenHosts.markState(it, DiscoveryHost.State.STANDBY)
		}
	}

	/**
	 * Re-attach after backgrounding: recreate the session if it was torn down while paused,
	 * restore the volume and (re)open the audio output.
	 */
	fun resumeStream(preferences: Preferences)
	{
		val session = session ?: return
		if(!session.disconnecting && session.session == null)
		{
			session.resume()
			session.setVolumePercent(preferences.audioVolume)
		}
		else
		{
			session.setVolumePercent(preferences.audioVolume)
			session.restartAudio()
		}
		// capture was stopped in onPause; restart it now that we are foreground again
		session.startMicIfNeeded()
	}

	/**
	 * Sends the console to bed via the live session. Returns true if a live session handled it;
	 * false when there is no (live) session and [sleepConsoleViaReconnect] should be used instead.
	 */
	fun sleepConsoleLive(): Boolean
	{
		val s = session ?: return false
		if(s.session == null)
			return false
		s.gotoBed()
		markDisplayHostStandby()
		return true
	}

	/**
	 * No live session left to talk to the console: open a short-lived session just to send
	 * the goto-bed command, then shut it down. Fire-and-forget.
	 */
	fun sleepConsoleViaReconnect(context: Context, preferences: Preferences, logManager: LogManager)
	{
		val host = displayHost ?: return
		val reg = host.registeredHost ?: return
		val addr = host.host
		if(addr.isEmpty())
			return
		scope.launch {
			try {
				val connectInfo = ConnectInfo(host.isPS5, addr, reg.rpRegistKey, reg.rpKey, preferences.videoProfile,
					enableIDROnFECFailure = preferences.idrOnFecFailure)
				val s = StreamSession(connectInfo, logManager, preferences.logVerbose, StreamInput(context.applicationContext, preferences))
				try
				{
					s.resume()
					val reached = withTimeoutOrNull(SLEEP_RECONNECT_TIMEOUT_MS) {
						s.state.first { it is StreamStateConnected || it is StreamStateQuit }
					}
					if(reached is StreamStateConnected)
					{
						s.gotoBed()
						delay(GOTO_BED_SETTLE_MS)
					}
					LastSeenHosts.markState(reg.serverMac, DiscoveryHost.State.STANDBY)
				}
				finally {
					withContext(Dispatchers.IO) { s.shutdown() }
				}
			} catch(e: Exception) {
				android.util.Log.e(TAG, "sleep-console reconnect failed", e)
			}
		}
	}
}
