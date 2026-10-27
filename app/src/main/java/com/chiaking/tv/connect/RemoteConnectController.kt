// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.connect

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.withTransaction
import com.chiaking.tv.R
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.logging.LogManager
import com.chiaking.tv.common.MacAddress
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.common.db.RegisteredHost
import com.chiaking.tv.common.psn.PsnAccountRepository
import com.chiaking.tv.session.StreamManager
import com.chiaking.tv.session.StreamSession
import com.chiaking.tv.session.StreamStateCreateError
import com.chiaking.tv.session.StreamStateQuit
import com.metallic.chiaki.lib.Holepunch
import com.metallic.chiaki.lib.HolepunchDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives Remote Play over Internet: PSN token refresh, holepunch session creation, first-time
 * PSN auto-registration (save host + reconnect) and the retry logic for a console that is still
 * busy from the registration session. Exposes [connectingText] as Compose state for the
 * home-screen banner; [cancel] aborts an in-flight attempt (including a running regist session).
 */
class RemoteConnectController(
	private val context: Context,
	private val scope: CoroutineScope,
	private val preferences: Preferences,
	private val database: AppDatabase,
	private val logManager: LogManager,
)
{
	companion object
	{
		const val TAG = "RemoteConnect"
		const val MAX_RETRIES = 3
		const val RETRY_DELAY_MS = 6000L
		const val REGIST_TIMEOUT_MS = 40000L
		const val SETTLE_DELAY_MS = 4000L
	}

	var connectingText by mutableStateOf<String?>(null)
		private set

	var onStreamReady: (StreamSession) -> Unit = {}

	private var job: Job? = null
	private var registSession: StreamSession? = null

	fun connect(device: HolepunchDevice)
	{
		if(StreamManager.session != null || connectingText != null)
			return
		start(device, internal = false, attempt = 0)
	}

	fun cancel()
	{
		job?.cancel()
		job = null
		connectingText = null
		registSession?.let { session ->
			session.disconnecting = true
		}
	}

	private fun start(device: HolepunchDevice, internal: Boolean, attempt: Int)
	{
		if(StreamManager.session != null || (connectingText != null && !internal))
			return
		val deviceName = device.name.ifBlank { context.getString(R.string.console) }
		connectingText = context.getString(R.string.remote_linking_with, deviceName)
		job = scope.launch {
			var ownedSession: StreamSession? = null
			var holepunchPtr = 0L
			try
			{
				connectingText = context.getString(R.string.remote_refreshing_token)
				val store = PsnAccountRepository.get(context)
				val token = withContext(Dispatchers.IO) { store.ensureFreshToken() }
				if(token == null)
				{
					connectingText = null
					Toast.makeText(context, R.string.remote_psn_required, Toast.LENGTH_LONG).show()
					return@launch
				}
				val accountIdBytes = store.accountIdBytes()
				if(accountIdBytes == null)
				{
					connectingText = null
					Toast.makeText(context, R.string.remote_account_missing, Toast.LENGTH_LONG).show()
					return@launch
				}
				connectingText = context.getString(R.string.remote_creating_session, deviceName)
				val result = withContext(Dispatchers.IO) {
					Holepunch.createSession(token, device.duid, device.isPS5).also {
						// Keep ownership even if cancellation discards the dispatcher result.
						holepunchPtr = it.ptr
					}
				}
				if(!result.errorCode.isSuccess)
				{
					// Right after auto-registration the console is busy finishing the regist session
					// and may not answer a new holepunch offer for a while — retry a few times.
					if(internal && attempt < MAX_RETRIES)
					{
						Log.i(TAG, "Holepunch create failed (${result.errorCode}), retry ${attempt + 1}/$MAX_RETRIES")
						delay(RETRY_DELAY_MS)
						start(device, internal = true, attempt = attempt + 1)
						return@launch
					}
					connectingText = null
					Log.e(TAG, "Holepunch create failed: ${result.errorCode}")
					Toast.makeText(context, context.getString(R.string.remote_connect_failed, result.errorCode), Toast.LENGTH_LONG).show()
					return@launch
				}
				// First connect to a console: the session only performs the PSN auto-registration
				// and then stops (upstream design). Save the host, then reconnect to actually stream.
				val autoRegist = device.duid !in preferences.autoRegistDuids
				if(StreamManager.session != null)
				{
					connectingText = null
					return@launch
				}
				// Native session creation consumes the handle, including its failure paths.
				holepunchPtr = 0L
				val session = StreamManager.startRemote(context, preferences, logManager,
					result.ptr, accountIdBytes, device.isPS5, autoRegist)
				if(session == null)
				{
					connectingText = null
					return@launch
				}
				ownedSession = session
				// Claim ownership immediately: a cancel during resolveRemoteLoginPin (suspend) or the
				// regist wait below must find the session here, otherwise it is orphaned mid-connect
				// (still holding the audio output and blocking StreamManager for the next connect).
				registSession = session
				if(!autoRegist)
				{
					resolveRemoteLoginPin(session, device.duid)
					connectingText = null
					registSession = null // handed off to the stream screen
					onStreamReady(session)
					ownedSession = null
					return@launch
				}
				val registOk = CompletableDeferred<Boolean>()
				var registSeen = false
				session.autoRegistCallback = { host ->
					registSeen = true
					scope.launch {
						try
						{
							val reg = RegisteredHost(host)
							database.withTransaction {
								val dao = database.registeredHostDao()
								val previous = dao.getByMac(reg.serverMac)
								if(previous == null)
									dao.insert(reg)
								else
									dao.update(reg.copy(id = previous.id, rpPin = previous.rpPin))
							}
							preferences.remoteHostMacs = preferences.remoteHostMacs
								.filterNot { it.startsWith("${device.duid}|") }.toSet() + "${device.duid}|${reg.serverMac}"
							preferences.autoRegistDuids = preferences.autoRegistDuids + device.duid
							Log.i(TAG, "Auto-registered console ${reg.serverMac} over PSN")
							registOk.complete(true)
						}
						catch(e: Exception)
						{
							Log.e(TAG, "Failed to save auto-registered host", e)
							registOk.complete(false)
						}
					}
				}
				val quitObserver = launch {
					session.state.collect { st ->
						if((st is StreamStateQuit || st is StreamStateCreateError) &&
							!registSeen && !registOk.isCompleted)
							registOk.complete(false)
					}
				}
				val ok = try {
					withTimeoutOrNull(REGIST_TIMEOUT_MS) { registOk.await() } ?: false
				} finally {
					quitObserver.cancel()
				}
				closeSession(session)
				ownedSession = null
				if(ok)
				{
					delay(SETTLE_DELAY_MS) // let the console settle after the regist session
					start(device, internal = true, attempt = 0)
				}
				else
				{
					connectingText = null
					Toast.makeText(context, R.string.regist_failed, Toast.LENGTH_LONG).show()
				}
			}
			catch(e: CancellationException)
			{
				throw e
			}
			catch(e: Exception)
			{
				Log.e(TAG, "Remote connect failed", e)
				connectingText = null
				Toast.makeText(context, context.getString(R.string.remote_connect_failed, e.message), Toast.LENGTH_LONG).show()
			}
			finally
			{
				ownedSession?.let { closeSession(it) }
				if(holepunchPtr != 0L)
					withContext(NonCancellable + Dispatchers.IO) { Holepunch.fini(holepunchPtr) }
			}
		}
	}

	private suspend fun closeSession(session: StreamSession)
	{
		if(registSession === session)
			registSession = null
		session.autoRegistCallback = null
		session.disconnecting = true
		StreamManager.clear(session)
		withContext(NonCancellable + Dispatchers.IO) { session.shutdown() }
	}

	private suspend fun resolveRemoteLoginPin(session: StreamSession, duid: String)
	{
		val mac = preferences.remoteHostMacs
			.firstOrNull { it.startsWith("$duid|") }
			?.substringAfter('|') ?: return
		val reg = database.registeredHostDao().getByMac(MacAddress(mac)) ?: return
		session.storedLoginPin = reg.rpPin
		session.loginPinHostId = reg.id
	}
}
