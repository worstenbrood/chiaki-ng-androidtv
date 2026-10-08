// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.session

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.*
import kotlinx.coroutines.flow.MutableStateFlow
import com.chiaking.tv.common.LogManager
import com.metallic.chiaki.lib.*

sealed class StreamState
object StreamStateIdle: StreamState()
object StreamStateConnecting: StreamState()
object StreamStateConnected: StreamState()
data class StreamStateCreateError(val error: CreateError): StreamState()
data class StreamStateQuit(val reason: QuitReason, val reasonString: String?): StreamState()
data class StreamStateLoginPinRequest(val pinIncorrect: Boolean): StreamState()

class StreamSession(val connectInfo: ConnectInfo, val logManager: LogManager, val logVerbose: Boolean, val input: StreamInput)
{
	var session: Session? = null
		private set

	private val _state = MutableStateFlow<StreamState>(StreamStateIdle)
	val state: MutableStateFlow<StreamState> get() = _state
	private val _rumbleState = MutableStateFlow(RumbleEvent(0U, 0U))
	val rumbleState: MutableStateFlow<RumbleEvent> get() = _rumbleState

	private var surfaceTexture: SurfaceTexture? = null
	private var surface: Surface? = null

	init
	{
		input.controllerStateChangedCallback = {
			session?.setControllerState(it)
		}
	}

	fun shutdown()
	{
		session?.stop()
		session?.dispose()
		session = null
		_state.value = StreamStateIdle
		//surfaceTexture?.release()
	}

	fun pause()
	{
		shutdown()
	}

	fun resume()
	{
		if(session != null)
			return
		try
		{
			val session = Session(connectInfo, logManager.createNewFile().file.absolutePath, logVerbose)
			_state.value = StreamStateConnecting
			session.eventCallback = this::eventCallback
			session.start()
			val surface = surface
			if(surface != null)
				session.setSurface(surface)
			this.session = session
		}
		catch(e: CreateError)
		{
			_state.value = StreamStateCreateError(e)
		}
	}

	private fun eventCallback(event: Event)
	{
		when(event)
		{
			is ConnectedEvent -> _state.value = StreamStateConnected
			is QuitEvent -> _state.value = StreamStateQuit(
					event.reason,
					event.reasonString
				)
			is LoginPinRequestEvent -> _state.value = StreamStateLoginPinRequest(
				event.pinIncorrect
			)
			is RumbleEvent -> _rumbleState.value = event
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
}
