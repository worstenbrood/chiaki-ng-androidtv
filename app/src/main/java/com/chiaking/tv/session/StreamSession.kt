// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.session

import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.util.Log
import android.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.chiaking.tv.common.AudioDevices
import com.chiaking.tv.common.logging.LogManager
import com.chiaking.tv.common.psn.PsnTokenStore
import com.metallic.chiaki.lib.*

class StreamSession(val connectInfo: ConnectInfo, val logManager: LogManager, val logVerbose: Boolean, val input: StreamInput)
{
	var session: Session? = null
		private set

	var autoRegistCallback: ((RegistHost) -> Unit)? = null

	/** Per-console login PIN (from the registered host); falls back to the global preference when null. */
	var storedLoginPin: String? = null

	/** Row id of the registered host [storedLoginPin] belongs to; manually entered PINs are stored here. */
	var loginPinHostId: Long? = null

	private val _state = MutableStateFlow<StreamState>(StreamStateIdle)
	val state: StateFlow<StreamState> get() = _state.asStateFlow()
	private val _rumbleState = MutableStateFlow(RumbleEvent(0U, 0U))
	val rumbleState: StateFlow<RumbleEvent> get() = _rumbleState.asStateFlow()
	private val _hudVisible = MutableStateFlow(false)
	val hudVisible: StateFlow<Boolean> get() = _hudVisible.asStateFlow()
	private val _disconnectDialogVisible = MutableStateFlow(false)
	val disconnectDialogVisible: StateFlow<Boolean> get() = _disconnectDialogVisible.asStateFlow()
	private val _micMuted = MutableStateFlow(true)
	val micMuted: StateFlow<Boolean> get() = _micMuted.asStateFlow()
	private val _micActive = MutableStateFlow(false)
	val micActive: StateFlow<Boolean> get() = _micActive.asStateFlow()
	@Volatile var disconnecting = false

	private var micCapture: MicCapture? = null
	val micPitch = MicPitch(input.preferences)

	private companion object
	{
		const val RETRY_WINDOW_MS = 20000L
		const val RETRY_DELAY_MS = 3000L
		const val MAX_RETRIES = 3
	}

	private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	@Volatile private var connected = false
	@Volatile private var startTime = 0L
	@Volatile private var retryCount = 0

	private var surfaceTexture: SurfaceTexture? = null
	private var surface: Surface? = null

	init
	{
		input.controllerStateChangedCallback = {
			session?.setControllerState(it)
		}
		input.hudToggleCallback = {
			_hudVisible.value = !_hudVisible.value
		}
		input.secondaryPadInputCallback = { slot ->
			val s = session
			val rid = coopSecondRid()
			if(s != null && rid != 0L)
			{
				android.util.Log.i("Multislot", "secondary pad slot=$slot first input -> addUser rid=$rid")
				s.addUser(slot, rid)
			}
			else
				android.util.Log.i("Multislot", "secondary pad slot=$slot but no second account (rid=$rid)")
		}
		_micMuted.value = !input.preferences.micStartUnmuted
	}

	/** Numeric rid of the first co-op extra account (base64 LE decoded), or 0 if none. */
	private fun coopSecondRid(): Long
	{
		val extra = PsnTokenStore.get(input.context).accounts().getOrNull(1) ?: return 0L
		val bytes = runCatching { android.util.Base64.decode(extra.rid, android.util.Base64.NO_WRAP) }.getOrNull()
		if(bytes == null || bytes.size < 8)
			return 0L
		var v = 0L
		for(i in 0 until 8)
			v = v or ((bytes[i].toLong() and 0xff) shl (8 * i))
		return v
	}

	/** True when the mic feature is on and we hold the RECORD_AUDIO permission. */
	fun micWanted(): Boolean =
		input.preferences.micEnabled && MicCapture.hasPermission(input.context)

	/** False when the device exposes no capture device at all (many TVs have no mic). */
	fun micAvailable(): Boolean =
		AudioDevices.inputDevices(input.context).size > 1

	/** Connects the console mic and starts capture; called on (re)connect and on foreground resume. */
	fun startMicIfNeeded()
	{
		val session = session ?: return
		// without a real capture device we would only feed silence while the console
		// believes a mic is connected, so leave the mic off entirely
		if(micCapture != null || !micWanted() || !micAvailable())
			return
		val capture = MicCapture(input.context, input.preferences) { session.feedMicFrame(it) }
		if(capture.start())
		{
			micCapture = capture
			session.startMic(_micMuted.value, input.preferences.micComplexity.value, input.preferences.micBandwidth.opusValue)
			micPitch.applyTo(session)
			_micActive.value = true
		}
	}

	/** Stops capture (backgrounding/teardown) but keeps the mute state so resume restores it. */
	fun stopMicCapture()
	{
		micCapture?.stop()
		micCapture = null
		micPitch.session = null
		_micActive.value = false
	}

	fun toggleMicMute()
	{
		val muted = !_micMuted.value
		_micMuted.value = muted
		session?.toggleMicrophone(muted)
	}

	fun getStats(): LongArray? = session?.getStats()

	@Volatile private var stopped = false
	@Volatile private var remoteStarted = false
	private var pinRequestId = 0L

	fun shutdown(finalState: StreamState = StreamStateIdle)
	{
		if(stopped)
			return
		stopped = true
		stopMicCapture()
		session?.stop()
		session?.dispose()
		session = null
		_state.value = finalState
		//surfaceTexture?.release()
	}

	fun pause()
	{
		shutdown()
	}

	fun applyAudioSettings(volumePercent: Int, deviceId: Int, bufferMs: Int)
	{
		session?.setVolume(volumePercent / 100f)
		if(deviceId != 0)
			session?.setAudioDevice(deviceId)
		if(bufferMs > 0)
			session?.setAudioBuffer(bufferMs)
	}

	fun setAudioDevice(deviceId: Int)
	{
		session?.setAudioDevice(deviceId)
	}

	fun setVolumePercent(volumePercent: Int)
	{
		session?.setVolume(volumePercent / 100f)
	}

	fun restartAudio()
	{
		session?.restartAudio()
	}

	fun resume()
	{
		if(session != null || disconnecting)
			return
		// A remote session's holepunch session is consumed (and freed) by the native session on quit,
		// so it cannot be re-established without a fresh holepunch handshake — require a manual reconnect
		if(connectInfo.holepunchSessionPtr != 0L && remoteStarted)
		{
			if(_state.value !is StreamStateQuit)
				_state.value = StreamStateQuit(QuitReason(-1), "Remote session ended — reconnect to play again")
			return
		}
		stopped = false
		connected = false
		retryCount = 0
		startTime = SystemClock.elapsedRealtime()
		try
		{
			val session = Session(connectInfo, logManager.createNewFile().file.absolutePath, logVerbose)
			if(connectInfo.holepunchSessionPtr != 0L)
				remoteStarted = true
			_state.value = StreamStateConnecting
			session.eventCallback = this::eventCallback
			session.start()
			val surface = surface
			if(surface != null)
			{
				session.setSurface(surface)
				session.requestIDR()
			}
			this.session = session
		}
		catch(e: CreateError)
		{
			_state.value = StreamStateCreateError(e)
		}
	}

	fun resumeDelayed(delayMs: Long)
	{
		retryCount = 0
		CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
			delay(delayMs)
			if(!disconnecting && session == null)
				resume()
		}
	}

	private fun eventCallback(event: Event)
	{
		if(stopped)
			return
		when(event)
		{
			is ConnectedEvent ->
			{
				connected = true
				retryCount = 0
				_state.value = StreamStateConnected
				startMicIfNeeded()
			}
			is QuitEvent ->
			{
				val elapsed = SystemClock.elapsedRealtime() - startTime
				if(!connected && retryCount < MAX_RETRIES && !disconnecting && event.reason.isError &&
					elapsed < RETRY_WINDOW_MS && connectInfo.holepunchSessionPtr == 0L)
				{
					retryCount++
					mainScope.launch {
						shutdown(StreamStateConnecting)
						delay(RETRY_DELAY_MS)
						if(!disconnecting && session == null)
							resume()
					}
				}
				else
					_state.value = StreamStateQuit(
							event.reason,
							event.reasonString
						)
			}
			is LoginPinRequestEvent -> _state.value = StreamStateLoginPinRequest(
				event.pinIncorrect, ++pinRequestId
			)
			is RumbleEvent -> _rumbleState.value = event
			is MotionResetEvent -> input.resetMotionControls()
			is DataHolepunchEvent -> _state.value = StreamStateRemoteProgress(event.finished)
			is AutoRegistEvent -> autoRegistCallback?.invoke(event.host)
			is VideoFecFailureEvent -> { }
		}
	}

	fun attachToSurfaceView(surfaceView: SurfaceView)
	{
		surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
			override fun surfaceCreated(holder: SurfaceHolder) { }

			override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int)
			{
				val surface = holder.surface
				this@StreamSession.surface = surface
				session?.setSurface(surface)
				session?.requestIDR()
			}

			override fun surfaceDestroyed(holder: SurfaceHolder)
			{
				this@StreamSession.surface = null
				session?.setSurface(null)
			}
		})
	}

	fun attachToTextureView(textureView: TextureView)
	{
		textureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
			override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int)
			{
				if(surfaceTexture != null)
					return
				surfaceTexture = surface
				this@StreamSession.surface = Surface(surfaceTexture)
				session?.setSurface(Surface(surface))
			}

			override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean
			{
				// return false if we want to keep the surface texture
				return surfaceTexture == null
			}

			override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { }
			override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
		}

		val surfaceTexture = surfaceTexture
		if(surfaceTexture != null)
			textureView.setSurfaceTexture(surfaceTexture)
	}

	fun setLoginPin(pin: String)
	{
		session?.setLoginPin(pin)
	}

	fun gotoBed()
	{
		session?.gotoBed()
	}

	/** Marks the session as disconnecting and switches to the disconnecting state. */
	fun markDisconnecting()
	{
		_state.value = StreamStateDisconnecting
	}

	/** Hides the HUD (Back while open). */
	fun hideHud()
	{
		_hudVisible.value = false
	}

	fun requestDisconnectDialog()
	{
		if(!disconnecting)
			_disconnectDialogVisible.value = true
	}

	fun dismissDisconnectDialog()
	{
		_disconnectDialogVisible.value = false
	}
}
