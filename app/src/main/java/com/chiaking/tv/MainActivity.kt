// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chiaking.tv.common.*
import com.chiaking.tv.discovery.serverMac
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.DiscoveryHost
import com.metallic.chiaki.lib.RegistInfo
import com.chiaking.tv.regist.RegistExecuteViewModel
import com.chiaking.tv.session.StreamInput
import com.chiaking.tv.session.StreamManager
import com.chiaking.tv.session.StreamSession
import com.chiaking.tv.session.StreamStateConnected
import com.chiaking.tv.session.StreamStateDisconnecting
import com.chiaking.tv.session.StreamStateQuit
import com.chiaking.tv.ui.screens.*
import com.chiaking.tv.ui.theme.ChiakiTvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.buffer
import okio.source

private sealed class Screen
private object ScreenHome: Screen()
private object ScreenSettings: Screen()
private class ScreenRegist(val host: String?, val ps5: Boolean?): Screen()
private class ScreenRegistExecute(val info: RegistInfo, val assignManualHostId: Long?): Screen()
private class ScreenStream(val session: StreamSession): Screen()
private object ScreenLogs: Screen()
private object ScreenManageHosts: Screen()

class MainActivity: ComponentActivity()
{
	companion object
	{
		const val EXTRA_AUTO_CONNECT_MAC = "com.chiaking.tv.AUTO_CONNECT_MAC"
		const val WAKE_TIMEOUT_MS = 25000L
		const val AUTO_CONNECT_DISCOVER_GRACE_MS = 5000L
		const val AWAY_TIMEOUT_MS = 60000L
	}

	private lateinit var preferences: Preferences
	private lateinit var database: AppDatabase
	private lateinit var logManager: LogManager
	private var wakingHost by mutableStateOf<DisplayHost?>(null)
	private var wakeBannerText by mutableStateOf<String?>(null)
	private var wakeJob: kotlinx.coroutines.Job? = null
	private var autoConnectTriggered = false
	private var deepLinkConnect = false
	private var pendingDeepLinkMac by mutableStateOf<MacAddress?>(null)
	private var awayJob: kotlinx.coroutines.Job? = null
	private var awaySleepPrompt by mutableStateOf(false)
	private var remoteConnectingText by mutableStateOf<String?>(null)

	private val screenStack = mutableStateListOf<Screen>()
	private var showManualHostDialog by mutableStateOf(false)
	private var editingManualHost by mutableStateOf<ManualHost?>(null)

	private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
		if(uri == null) return@registerForActivityResult
		lifecycleScope.launch {
			val json = exportAllSettings(database)
			contentResolver.openOutputStream(uri)?.use { out ->
				out.write(json.toByteArray())
			}
		}
	}

	private var pendingImport by mutableStateOf<SerializedSettings?>(null)

	private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if(uri == null) return@registerForActivityResult
		lifecycleScope.launch {
			try {
				val settings = contentResolver.openInputStream(uri)!!.source().buffer().use {
					parseSettingsImport(it)
				}
				pendingImport = settings
			} catch(e: Exception) {
				android.util.Log.e("MainActivity", "Import failed", e)
				android.widget.Toast.makeText(this@MainActivity, getString(R.string.import_failed, e.message), android.widget.Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun doImport(replace: Boolean)
	{
		val settings = pendingImport ?: return
		pendingImport = null
		lifecycleScope.launch {
			try {
				importSettings(database, settings, replace)
				android.widget.Toast.makeText(this@MainActivity, getString(R.string.imported_count, settings.registeredHosts.size), android.widget.Toast.LENGTH_SHORT).show()
			} catch(e: Exception) {
				android.util.Log.e("MainActivity", "Import failed", e)
				android.widget.Toast.makeText(this@MainActivity, getString(R.string.import_failed, e.message), android.widget.Toast.LENGTH_LONG).show()
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

		preferences = Preferences(this)
		database = getDatabase(this)
		logManager = LogManager(this)
		com.chiaking.tv.common.psn.CaBundle.ensure(this)
		screenStack.add(ScreenHome)
		StreamManager.session?.let { screenStack.add(ScreenStream(it)) }

		setContent {
			ChiakiTvTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = Color(0xFF0B0E17)
				) {
					Box {
						Navigation()
						pendingImport?.let {
							androidx.compose.material3.AlertDialog(
								onDismissRequest = { pendingImport = null },
								title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.import_settings)) },
								text = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.import_replace_merge_question)) },
								confirmButton = {
									androidx.compose.material3.TextButton(onClick = { doImport(true) })
									{ androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.replace)) }
								},
								dismissButton = {
									androidx.compose.material3.TextButton(onClick = { doImport(false) })
									{ androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.merge)) }
								}
							)
						}
					}
				}
			}
		}

		lifecycleScope.launch {
			database.registeredHostDao().getAll().collect { hosts ->
				withContext(Dispatchers.IO) { TvChannel.sync(this@MainActivity, preferences, hosts) }
			}
		}
		handleAutoConnect(intent)
	}

	override fun onNewIntent(intent: Intent)
	{
		super.onNewIntent(intent)
		handleAutoConnect(intent)
	}

	private fun handleAutoConnect(intent: Intent?)
	{
		val macStr = intent?.getStringExtra(EXTRA_AUTO_CONNECT_MAC) ?: return
		intent.removeExtra(EXTRA_AUTO_CONNECT_MAC)
		val mac = try { MacAddress(macStr) } catch(e: IllegalArgumentException) { return }
		deepLinkConnect = true
		pendingDeepLinkMac = mac
	}

	private fun deepLinkConnect(viewModel: MainViewModel, mac: MacAddress)
	{
		pendingDeepLinkMac = null
		lifecycleScope.launch {
			val reg = withContext(Dispatchers.IO) { database.registeredHostDao().getByMac(mac) }
				?: return@launch
			var found: DiscoveryHost? = null
			val deadline = android.os.SystemClock.elapsedRealtime() + 15000L
			while(found == null && android.os.SystemClock.elapsedRealtime() < deadline)
			{
				found = viewModel.discoveryManager.discoveredHosts.value.firstOrNull { it.serverMac == mac }
					?: LastSeenHosts.hosts.value[mac]?.takeIf { !it.hostAddr.isNullOrEmpty() }
				if(found == null)
					kotlinx.coroutines.delay(500)
			}
			if(found == null || screenStack.lastOrNull() !is ScreenHome)
				return@launch
			if(found.state == DiscoveryHost.State.READY)
				startStream(DiscoveredDisplayHost(reg, found))
			else if(found.state == DiscoveryHost.State.STANDBY)
				wakeupAndConnect(viewModel, DiscoveredDisplayHost(reg, found), getString(R.string.connecting_to))
		}
	}

	@Composable
	private fun Navigation()
	{
		BackHandler(enabled = wakingHost != null) { cancelWakeup() }
		BackHandler(enabled = screenStack.size > 1 || showManualHostDialog)
		{
			if(showManualHostDialog)
				showManualHostDialog = false
			else if(screenStack.size > 1)
				screenStack.removeAt(screenStack.size - 1)
		}
		when(val screen = screenStack.last())
		{
			ScreenHome -> {
				val viewModel = remember { MainViewModel(this, database, preferences) }
				val hosts by viewModel.displayHosts.collectAsState()
				val discoveryActive by viewModel.discoveryActive.collectAsState()
				val psnLoggedIn by viewModel.psnLoggedIn.collectAsState()
				val psnDevices by viewModel.psnDevices.collectAsState()
				val psnLoading by viewModel.psnLoading.collectAsState()
				val psnDeviceCount by viewModel.psnDeviceCount.collectAsState()
				LaunchedEffect(Unit) { viewModel.discoveryManager.resume() }
				LaunchedEffect(pendingDeepLinkMac) { pendingDeepLinkMac?.let { deepLinkConnect(viewModel, it) } }
				LaunchedEffect(Unit) { maybeAutoConnect(viewModel) }
				LaunchedEffect(Unit) { viewModel.refreshPsnLoginState(); if(viewModel.psnLoggedIn.value) viewModel.refreshPsnDevices() }
				DisposableEffect(Unit) { onDispose { viewModel.discoveryManager.pause() } }
				Box {
					HomeScreen(hosts, discoveryActive, wakingHost, wakeBannerText, { cancelWakeup() },
						psnLoggedIn = psnLoggedIn,
						psnDevices = psnDevices,
						psnLoading = psnLoading,
						psnDeviceCount = psnDeviceCount,
						remoteConnectingText = remoteConnectingText
					) { action ->
						when(action)
						{
							is ConnectHost -> startStream(action.host)
							is WakeupHost -> wakeupAndConnect(viewModel, action.host)
							is ConnectRemoteConsole -> connectRemote(action.device)
							RefreshPsnDevices -> viewModel.refreshPsnDevices()
							is EditManualHost -> {
								editingManualHost = action.manualHost
								showManualHostDialog = true
							}
							is DeleteManualHost -> viewModel.deleteManualHost(action.manualHost)
							RegisterNew -> screenStack.add(ScreenRegist(null, null))
							is RegisterHost -> screenStack.add(ScreenRegist(action.host, action.ps5))
							AddManualHost -> {
								editingManualHost = null
								showManualHostDialog = true
							}
							OpenSettings -> screenStack.add(ScreenSettings)
							ToggleDiscovery -> viewModel.discoveryManager.active = !viewModel.discoveryManager.active
						}
					}
					if(showManualHostDialog)
						ManualHostDialog(
							initial = editingManualHost?.host ?: "",
							onDismiss = { showManualHostDialog = false },
							onSave = { value ->
								val current = editingManualHost
								if(current != null)
									viewModel.updateManualHost(current, value)
								else
									viewModel.addManualHost(value)
								showManualHostDialog = false
							}
						)
				}
			}

			ScreenSettings -> {
				val registeredHosts = remember { mutableStateOf<List<RegisteredHost>>(emptyList()) }
				LaunchedEffect(Unit) {
					database.registeredHostDao().getAll().collect { registeredHosts.value = it }
				}
				SettingsScreen(
					preferences = preferences,
					registeredHostCount = registeredHosts.value.size,
					registeredHosts = registeredHosts.value,
					onBack = { screenStack.removeAt(screenStack.size - 1) },
					onShowLogs = { screenStack.add(ScreenLogs) },
					onManageHosts = { screenStack.add(ScreenManageHosts) },
					onExportSettings = { exportLauncher.launch("chiaki-settings.json") },
					onImportSettings = { importLauncher.launch(arrayOf("application/json")) }
				)
			}

			is ScreenRegist -> RegistScreen(
				initialHost = screen.host,
				initialPs5 = screen.ps5,
				onBack = { screenStack.removeAt(screenStack.size - 1) },
				onStartRegist = { info, assignId ->
					screenStack.add(ScreenRegistExecute(info, assignId))
				}
			)

			is ScreenRegistExecute -> {
				val registViewModel = remember { RegistExecuteViewModel(database) }
				LaunchedEffect(Unit) { registViewModel.start(screen.info, screen.assignManualHostId) }
				RegistExecuteScreen(registViewModel) {
					repeat(2) { if(screenStack.size > 1) screenStack.removeAt(screenStack.size - 1) }
				}
			}

			is ScreenStream -> {
				ObserveRumble(screen.session)
				StreamScreen(screen.session, preferences,
					onQuit = { disconnectStream() },
					onSleep =
					{
						StreamManager.displayHost?.registeredHost?.serverMac?.let {
							LastSeenHosts.markState(it, DiscoveryHost.State.STANDBY)
						}
						disconnectStream()
					})
				if(awaySleepPrompt)
					androidx.compose.material3.AlertDialog(
						onDismissRequest = { keepPlaying() },
						title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.console_still_awake)) },
						text = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.away_sleep_question)) },
						confirmButton = {
							androidx.compose.material3.TextButton(onClick = {
								awaySleepPrompt = false
								sleepConsoleAway()
							}) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.sleep_console)) }
						},
						dismissButton = {
							androidx.compose.material3.TextButton(onClick = { keepPlaying() })
							{ androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.keep_playing)) }
						}
					)
			}

			ScreenLogs -> LogsScreen(logManager) { screenStack.removeAt(screenStack.size - 1) }

			ScreenManageHosts -> {
				val viewModel = viewModel(this, database, preferences)
				val hosts = remember { mutableStateOf<List<RegisteredHost>>(emptyList()) }
				LaunchedEffect(Unit) {
					database.registeredHostDao().getAll().collect { hosts.value = it }
				}
				ManageHostsScreen(hosts.value, { viewModel.deleteRegisteredHost(it) }) {
					screenStack.removeAt(screenStack.size - 1)
				}
			}
		}
	}

	@Composable
	private fun viewModel(context: android.content.Context, db: AppDatabase, prefs: Preferences) =
		androidx.lifecycle.viewmodel.compose.viewModel { MainViewModel(context, db, prefs) }

	private fun wakeupAndConnect(viewModel: MainViewModel, host: DisplayHost, message: String = getString(R.string.waking))
	{
		val reg = host.registeredHost ?: return
		if(wakingHost?.host == host.host)
		{
			cancelWakeup()
			return
		}
		cancelWakeup()
		viewModel.discoveryManager.sendWakeup(host.host, reg.rpRegistKey, reg.target.isPS5)
		val mac = (host as? DiscoveredDisplayHost)?.discoveredHost?.serverMac ?: return
		wakingHost = host
		wakeBannerText = getString(R.string.wake_waiting, message, host.name ?: getString(R.string.console))
		wakeJob = lifecycleScope.launch {
			val deadline = android.os.SystemClock.elapsedRealtime() + WAKE_TIMEOUT_MS
			var ready: DiscoveryHost? = null
			while(ready == null && android.os.SystemClock.elapsedRealtime() < deadline)
			{
				ready = viewModel.discoveryManager.discoveredHosts.value.firstOrNull {
					it.serverMac == mac && it.state == DiscoveryHost.State.READY
				}
				if(ready == null)
					kotlinx.coroutines.delay(500)
			}
			wakingHost = null
			wakeBannerText = null
			wakeJob = null
			if(ready != null && screenStack.lastOrNull() is ScreenHome)
				startStream(DiscoveredDisplayHost(reg, ready))
		}
	}

	private fun cancelWakeup()
	{
		wakeJob?.cancel()
		wakeJob = null
		wakingHost = null
		wakeBannerText = null
	}

	private fun maybeAutoConnect(viewModel: MainViewModel)
	{
		if(autoConnectTriggered || deepLinkConnect)
			return
		if(!preferences.autoConnectEnabled)
			return
		autoConnectTriggered = true
		val macStr = preferences.autoConnectMac
		if(macStr.isEmpty())
			return
		val mac = try { MacAddress(macStr) } catch(e: IllegalArgumentException) { return }
		lifecycleScope.launch {
			val reg = withContext(Dispatchers.IO) { database.registeredHostDao().getByMac(mac) }
				?: return@launch
			var found: DiscoveryHost? = null
			val deadline = android.os.SystemClock.elapsedRealtime() + AUTO_CONNECT_DISCOVER_GRACE_MS
			while(found == null && android.os.SystemClock.elapsedRealtime() < deadline)
			{
				found = viewModel.discoveryManager.discoveredHosts.value.firstOrNull { it.serverMac == mac }
				if(found == null)
					kotlinx.coroutines.delay(500)
			}
			if(found == null || screenStack.lastOrNull() !is ScreenHome)
				return@launch
			if(found.state == DiscoveryHost.State.READY)
				startStream(DiscoveredDisplayHost(reg, found))
			else if(found.state == DiscoveryHost.State.STANDBY)
				wakeupAndConnect(viewModel, DiscoveredDisplayHost(reg, found), getString(R.string.auto_connecting_to))
		}
	}

	private fun connectRemote(device: com.metallic.chiaki.lib.HolepunchDevice, internal: Boolean = false, attempt: Int = 0)
	{
		if(StreamManager.session != null || (remoteConnectingText != null && !internal))
			return
		val deviceName = device.name.ifBlank { getString(R.string.console) }
		remoteConnectingText = getString(R.string.remote_linking_with, deviceName)
		lifecycleScope.launch {
			try
			{
				remoteConnectingText = getString(R.string.remote_refreshing_token)
				val store = com.chiaking.tv.common.psn.PsnTokenStore.get(this@MainActivity)
				val token = withContext(Dispatchers.IO) { store.ensureFreshToken() }
				if(token == null)
				{
					remoteConnectingText = null
					android.widget.Toast.makeText(this@MainActivity, R.string.remote_psn_required, android.widget.Toast.LENGTH_LONG).show()
					return@launch
				}
				val accountIdBytes = store.accountIdBytes()
				if(accountIdBytes == null)
				{
					remoteConnectingText = null
					android.widget.Toast.makeText(this@MainActivity, R.string.remote_account_missing, android.widget.Toast.LENGTH_LONG).show()
					return@launch
				}
				remoteConnectingText = getString(R.string.remote_creating_session, deviceName)
				val result = withContext(Dispatchers.IO) { com.metallic.chiaki.lib.Holepunch.createSession(token, device.duid, device.isPS5) }
				if(!result.errorCode.isSuccess)
				{
					// Right after auto-registration the console is busy finishing the regist session
					// and may not answer a new holepunch offer for a while — retry a few times.
					if(internal && attempt < 3)
					{
						android.util.Log.i("MainActivity", "Holepunch create failed (${result.errorCode}), retry ${attempt + 1}/3")
						delay(6000)
						connectRemote(device, internal = true, attempt = attempt + 1)
						return@launch
					}
					remoteConnectingText = null
					android.util.Log.e("MainActivity", "Holepunch create failed: ${result.errorCode}")
					android.widget.Toast.makeText(this@MainActivity, getString(R.string.remote_connect_failed, result.errorCode), android.widget.Toast.LENGTH_LONG).show()
					return@launch
				}
				// First connect to a console: the session only performs the PSN auto-registration
				// and then stops (upstream design). Save the host, then reconnect to actually stream.
				val autoRegist = device.duid !in preferences.autoRegistDuids
				val session = StreamManager.startRemote(this@MainActivity, preferences, logManager,
					result.ptr, accountIdBytes, device.isPS5, autoRegist)
				if(session == null)
				{
					com.metallic.chiaki.lib.Holepunch.fini(result.ptr)
					remoteConnectingText = null
					return@launch
				}
				if(!autoRegist)
				{
					remoteConnectingText = null
					screenStack.add(ScreenStream(session))
					return@launch
				}
				val registOk = kotlinx.coroutines.CompletableDeferred<Boolean>()
				var registSeen = false
				session.autoRegistCallback = { host ->
					registSeen = true
					lifecycleScope.launch {
						try
						{
							val reg = RegisteredHost(host)
							database.registeredHostDao().deleteByMac(reg.serverMac)
							database.registeredHostDao().insert(reg)
							preferences.autoRegistDuids = preferences.autoRegistDuids + device.duid
							android.util.Log.i("MainActivity", "Auto-registered console ${reg.serverMac} over PSN")
							registOk.complete(true)
						}
						catch(e: Exception)
						{
							android.util.Log.e("MainActivity", "Failed to save auto-registered host", e)
							registOk.complete(false)
						}
					}
				}
				lifecycleScope.launch {
					session.state.collect { st ->
						if(st is StreamStateQuit && !registSeen && !registOk.isCompleted)
							registOk.complete(false)
					}
				}
				val ok = withTimeoutOrNull(40000) { registOk.await() } ?: false
				StreamManager.clear(session)
				if(ok)
				{
					delay(4000) // let the console settle after the regist session
					connectRemote(device, internal = true)
				}
				else
				{
					remoteConnectingText = null
					android.widget.Toast.makeText(this@MainActivity, R.string.regist_failed, android.widget.Toast.LENGTH_LONG).show()
				}
			}
			catch(e: Exception)
			{
				android.util.Log.e("MainActivity", "Remote connect failed", e)
				remoteConnectingText = null
				android.widget.Toast.makeText(this@MainActivity, getString(R.string.remote_connect_failed, e.message), android.widget.Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun startStream(host: DisplayHost)
	{
		cancelWakeup()
		awayJob?.cancel()
		awayJob = null
		awaySleepPrompt = false
		val session = StreamManager.start(this, preferences, logManager, host) ?: return
		screenStack.add(ScreenStream(session))
	}

	private fun disconnectStream()
	{
		awayJob?.cancel()
		awayJob = null
		awaySleepPrompt = false
		val screen = screenStack.lastOrNull() as? ScreenStream ?: return
		if(screen.session.disconnecting)
			return
		screen.session.disconnecting = true
		screen.session.state.value = StreamStateDisconnecting
		StreamManager.clear(screen.session)
		lifecycleScope.launch {
			withContext(Dispatchers.IO) { screen.session.shutdown(StreamStateDisconnecting) }
			if(screenStack.lastOrNull() === screen)
				screenStack.removeAt(screenStack.size - 1)
		}
	}

	override fun onResume()
	{
		super.onResume()
		awayJob?.cancel()
		awayJob = null
		if(awaySleepPrompt)
			return
		resumeStream()
	}

	private fun resumeStream()
	{
		val session = StreamManager.session ?: return
		if(screenStack.lastOrNull() !is ScreenStream)
			return
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
	}

	private fun keepPlaying()
	{
		awaySleepPrompt = false
		resumeStream()
	}

	override fun onPause()
	{
		super.onPause()
		val session = StreamManager.session
		if(session != null && !session.disconnecting && session.session != null &&
			screenStack.lastOrNull() is ScreenStream &&
			preferences.sleepConsoleOnExit != Preferences.SleepConsoleOnExit.NEVER)
		{
			session.setVolumePercent(0)
			awayJob = lifecycleScope.launch {
				delay(AWAY_TIMEOUT_MS)
				awayJob = null
				if(preferences.sleepConsoleOnExit == Preferences.SleepConsoleOnExit.ALWAYS)
					sleepConsoleAway()
				else
					awaySleepPrompt = true
			}
		}
		else
			session?.pause()
	}

	private fun sleepConsoleAway()
	{
		val session = StreamManager.session
		if(session != null && session.session != null)
		{
			session.gotoBed()
			StreamManager.displayHost?.registeredHost?.serverMac?.let {
				LastSeenHosts.markState(it, DiscoveryHost.State.STANDBY)
			}
			disconnectStream()
			return
		}
		sleepConsoleViaReconnect(StreamManager.displayHost)
		StreamManager.clear()
		if(screenStack.lastOrNull() is ScreenStream)
			screenStack.removeAt(screenStack.size - 1)
	}

	private fun sleepConsoleViaReconnect(host: DisplayHost?)
	{
		val reg = host?.registeredHost ?: return
		val addr = host.host
		if(addr.isEmpty())
			return
		lifecycleScope.launch {
			try {
				val connectInfo = ConnectInfo(host.isPS5, addr, reg.rpRegistKey, reg.rpKey, preferences.videoProfile)
				val s = StreamSession(connectInfo, logManager, preferences.logVerbose, StreamInput(applicationContext, preferences))
				try
				{
					s.resume()
					val reached = withTimeoutOrNull(10000L) {
						s.state.first { it is StreamStateConnected || it is StreamStateQuit }
					}
					if(reached is StreamStateConnected)
					{
						s.gotoBed()
						delay(500)
					}
					LastSeenHosts.markState(reg.serverMac, DiscoveryHost.State.STANDBY)
				}
				finally {
					withContext(Dispatchers.IO) { s.shutdown() }
				}
			} catch(e: Exception) {
				android.util.Log.e("MainActivity", "sleep-console reconnect failed", e)
			}
		}
	}

	@Composable
	private fun ObserveRumble(session: StreamSession)
	{
		if(!preferences.rumbleEnabled)
			return
		LaunchedEffect(session) {
			val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				session.rumbleState.collectLatest { rumble ->
					val amplitude = (rumble.left.toInt() + rumble.right.toInt()) / 2
					vibrator.cancel()
					if(amplitude == 0) return@collectLatest
					if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
						vibrator.vibrate(VibrationEffect.createOneShot(1000, amplitude))
					else
						@Suppress("DEPRECATION") vibrator.vibrate(1000)
				}
			}
		}
	}

	override fun onDestroy()
	{
		super.onDestroy()
		if(!isChangingConfigurations)
		{
			StreamManager.session?.shutdown()
			StreamManager.clear()
		}
	}

	override fun dispatchKeyEvent(event: KeyEvent): Boolean
	{
		if(screenStack.lastOrNull() is ScreenStream)
		{
			val session = (screenStack.last() as ScreenStream).session
			if(event.keyCode == KeyEvent.KEYCODE_BACK)
			{
				if(session.hudVisible.value)
				{
					if(event.action == KeyEvent.ACTION_UP)
						session.hudVisible.value = false
					return true
				}
				if(session.disconnectDialogVisible.value)
				{
					if(event.action == KeyEvent.ACTION_UP)
						session.dismissDisconnectDialog()
					return true
				}
				// BACK from the gamepad (Create button) goes to the console as the PS button;
				// BACK from the remote opens the Sleep/Disconnect dialog
				if(session.state.value is StreamStateConnected)
					StreamManager.input?.let { if(it.dispatchKeyEvent(event)) return true }
				if(event.action == KeyEvent.ACTION_UP)
				{
					if(session.state.value is StreamStateConnected)
						when(preferences.disconnectAction)
						{
							Preferences.DisconnectAction.SLEEP -> sleepConsoleAway()
							Preferences.DisconnectAction.NOTHING -> disconnectStream()
							Preferences.DisconnectAction.ASK -> session.requestDisconnectDialog()
						}
					else
						disconnectStream()
				}
				return true
			}
			// L3+R3 always toggles the HUD, even while it is open (and never reaches the console)
			if(StreamManager.input?.onHudComboKeyEvent(event) == true)
				return true
			// While the HUD is open, only D-pad + cross navigate it — everything else goes to the game
			if(session.hudVisible.value)
			{
				if(event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
					event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
					event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
					return super.dispatchKeyEvent(event)
				StreamManager.input?.let { if(it.dispatchKeyEvent(event)) return true }
			}
			else
				StreamManager.input?.let { if(it.dispatchKeyEvent(event)) return true }
		}
		return super.dispatchKeyEvent(event)
	}

	override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean
	{
		if(screenStack.lastOrNull() is ScreenStream)
		{
			if(hudStickNavigate(event))
				return true
			// While the HUD is open, gamepad sticks go straight to the game (Compose would eat them)
			val session = (screenStack.last() as ScreenStream).session
			if(session.hudVisible.value &&
				event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0)
			{
				StreamManager.input?.onGenericMotionEvent(event)
				return true
			}
		}
		return super.dispatchGenericMotionEvent(event)
	}

	override fun onGenericMotionEvent(event: MotionEvent): Boolean
	{
		if(screenStack.lastOrNull() is ScreenStream)
		{
			StreamManager.input?.let { if(it.onGenericMotionEvent(event)) return true }
		}
		return super.onGenericMotionEvent(event)
	}

	private var hudStickDir = 0

	private fun hudStickNavigate(event: MotionEvent): Boolean
	{
		if(event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) == 0)
			return false
		val session = (screenStack.last() as ScreenStream).session
		if(!session.hudVisible.value)
		{
			hudStickDir = 0
			return false
		}
		val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
		val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
		val dir = when
		{
			y < -0.5f -> KeyEvent.KEYCODE_DPAD_UP
			y > 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
			x < -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
			x > 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
			else -> 0
		}
		if(dir == 0)
		{
			hudStickDir = 0
			return false
		}
		if(dir == hudStickDir)
			return true
		hudStickDir = dir
		android.util.Log.i("HudNav", "hat dir=$dir x=$x y=$y src=${event.source}")
		dispatchKeyEvent(KeyEvent(event.eventTime, event.eventTime, KeyEvent.ACTION_DOWN, dir, 0))
		dispatchKeyEvent(KeyEvent(event.eventTime, event.eventTime, KeyEvent.ACTION_UP, dir, 0))
		return true
	}
}

@androidx.compose.runtime.Composable
private fun ManualHostDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit)
{
	var value by remember { mutableStateOf(initial) }
	androidx.compose.material3.AlertDialog(
		onDismissRequest = onDismiss,
		title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(if(initial.isEmpty()) R.string.manual_host_add else R.string.manual_host_edit)) },
		text = {
			com.chiaking.tv.ui.screens.TvTextField(androidx.compose.ui.res.stringResource(R.string.host_address_ip), value) { value = it }
		},
		confirmButton = {
			androidx.compose.material3.TextButton(
				onClick = { if(value.isNotBlank()) onSave(value.trim()) }
			) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.save)) }
		},
		dismissButton = {
			androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.cancel)) }
		}
	)
}
