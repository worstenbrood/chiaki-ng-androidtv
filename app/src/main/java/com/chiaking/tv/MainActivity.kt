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
import androidx.lifecycle.lifecycleScope
import com.chiaking.tv.common.*
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.DiscoveryHost
import com.metallic.chiaki.lib.RegistInfo
import com.chiaking.tv.regist.RegistExecuteViewModel
import com.chiaking.tv.session.StreamInput
import com.chiaking.tv.session.StreamSession
import com.chiaking.tv.session.StreamStateConnected
import com.chiaking.tv.session.StreamStateDisconnecting
import com.chiaking.tv.ui.screens.*
import com.chiaking.tv.ui.theme.ChiakiTvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

		private val HUD_SWALLOWED_KEYS = setOf(
			KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B,
			KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
			KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
			KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START,
			KeyEvent.KEYCODE_BUTTON_MODE,
			KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR
		)
	}

	private lateinit var preferences: Preferences
	private lateinit var database: AppDatabase
	private lateinit var logManager: LogManager
	private var streamInput: StreamInput? = null
	private var currentStreamSession: StreamSession? = null
	private var currentStreamDisplayHost: DisplayHost? = null

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

	private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if(uri == null) return@registerForActivityResult
		lifecycleScope.launch {
			try {
				val settings = contentResolver.openInputStream(uri)!!.source().buffer().use {
					parseSettingsImport(it)
				}
				importSettings(database, settings)
			} catch(e: Exception) {
				// surfaced via toast-less UI; log for debugging
				android.util.Log.e("MainActivity", "Import failed", e)
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
		screenStack.add(ScreenHome)

		setContent {
			ChiakiTvTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = Color(0xFF0B0E17)
				) {
					Navigation()
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
		lifecycleScope.launch {
			val hosts = withContext(Dispatchers.IO) { database.registeredHostDao().getAll().first() }
			val reg = hosts.firstOrNull { it.serverMac == mac } ?: return@launch
			if(screenStack.lastOrNull() is ScreenStream)
				return@launch
			var discovered: DiscoveryHost? = null
			val deadline = android.os.SystemClock.elapsedRealtime() + 15000L
			while(discovered == null && android.os.SystemClock.elapsedRealtime() < deadline)
			{
				discovered = LastSeenHosts.hosts.value[mac]?.takeIf { !it.hostAddr.isNullOrEmpty() }
				if(discovered == null)
					kotlinx.coroutines.delay(500)
			}
			discovered ?: return@launch
			if(screenStack.lastOrNull() is ScreenStream)
				return@launch
			startStream(DiscoveredDisplayHost(reg, discovered))
		}
	}

	@Composable
	private fun Navigation()
	{
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
				val viewModel = remember { MainViewModel(database, preferences) }
				val hosts by viewModel.displayHosts.collectAsState()
				val discoveryActive by viewModel.discoveryActive.collectAsState()
				LaunchedEffect(Unit) { viewModel.discoveryManager.resume() }
				DisposableEffect(Unit) { onDispose { viewModel.discoveryManager.pause() } }
				Box {
					HomeScreen(hosts, discoveryActive) { action ->
						when(action)
						{
							is ConnectHost -> startStream(action.host)
							is WakeupHost -> {
								val reg = action.host.registeredHost ?: return@HomeScreen
								viewModel.discoveryManager.sendWakeup(action.host.host, reg.rpRegistKey, reg.target.isPS5)
							}
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
				val registeredCount by viewModel(database, preferences).registeredHostCount.collectAsState()
				SettingsScreen(
					preferences = preferences,
					registeredHostCount = registeredCount,
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

			is ScreenStream -> StreamScreen(screen.session, preferences,
				onQuit = { disconnectStream() },
				onSleep =
				{
					currentStreamDisplayHost?.registeredHost?.serverMac?.let {
						LastSeenHosts.markState(it, DiscoveryHost.State.STANDBY)
					}
					disconnectStream()
				})

			ScreenLogs -> LogsScreen(logManager) { screenStack.removeAt(screenStack.size - 1) }

			ScreenManageHosts -> {
				val viewModel = viewModel(database, preferences)
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
	private fun viewModel(db: AppDatabase, prefs: Preferences) =
		androidx.lifecycle.viewmodel.compose.viewModel { MainViewModel(db, prefs) }

	private fun startStream(host: DisplayHost)
	{
		val registeredHost = host.registeredHost ?: return
		val connectInfo = ConnectInfo(
			host.isPS5,
			host.host,
			registeredHost.rpRegistKey,
			registeredHost.rpKey,
			preferences.videoProfile
		)
		val input = StreamInput(this, preferences)
		val session = StreamSession(connectInfo, logManager, preferences.logVerbose, input)
		streamInput = input
		currentStreamSession = session
		currentStreamDisplayHost = host
		session.resume()
		session.applyAudioSettings(preferences.audioVolume,
			AudioDevices.resolveDeviceId(this, preferences.audioDeviceName, preferences.audioDeviceId),
			preferences.audioBufferMs)

		if(preferences.rumbleEnabled)
		{
			val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
			lifecycleScope.launch {
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

		screenStack.add(ScreenStream(session))
	}

	private fun disconnectStream()
	{
		val screen = screenStack.lastOrNull() as? ScreenStream ?: return
		if(screen.session.disconnecting)
			return
		screen.session.disconnecting = true
		screen.session.state.value = StreamStateDisconnecting
		lifecycleScope.launch {
			withContext(Dispatchers.IO) { screen.session.shutdown(StreamStateDisconnecting) }
			if(currentStreamSession === screen.session)
			{
				currentStreamSession = null
				currentStreamDisplayHost = null
			}
			if(screenStack.lastOrNull() === screen)
				screenStack.removeAt(screenStack.size - 1)
		}
	}

	override fun onResume()
	{
		super.onResume()
		currentStreamSession?.let { if(screenStack.lastOrNull() is ScreenStream) it.resume() }
	}

	override fun onPause()
	{
		super.onPause()
		currentStreamSession?.pause()
	}

	override fun onDestroy()
	{
		super.onDestroy()
		currentStreamSession?.shutdown()
		currentStreamSession = null
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
					streamInput?.let { if(it.dispatchKeyEvent(event)) return true }
				if(event.action == KeyEvent.ACTION_UP)
				{
					if(session.state.value is StreamStateConnected)
						session.requestDisconnectDialog()
					else
						disconnectStream()
				}
				return true
			}
			// L3+R3 always toggles the HUD, even while it is open (and never reaches the console)
			if(streamInput?.onHudComboKeyEvent(event) == true)
				return true
			// While the HUD is open, only cross/DPad navigate it — swallow other gamepad buttons
			if(session.hudVisible.value)
			{
				if(event.keyCode in HUD_SWALLOWED_KEYS)
					return true
			}
			else
				streamInput?.let { if(it.dispatchKeyEvent(event)) return true }
		}
		return super.dispatchKeyEvent(event)
	}

	override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean
	{
		if(screenStack.lastOrNull() is ScreenStream && hudStickNavigate(event))
			return true
		return super.dispatchGenericMotionEvent(event)
	}

	override fun onGenericMotionEvent(event: MotionEvent): Boolean
	{
		if(screenStack.lastOrNull() is ScreenStream)
		{
			streamInput?.let { if(it.onGenericMotionEvent(event)) return true }
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
		val x = event.getAxisValue(MotionEvent.AXIS_X) + event.getAxisValue(MotionEvent.AXIS_HAT_X)
		val y = event.getAxisValue(MotionEvent.AXIS_Y) + event.getAxisValue(MotionEvent.AXIS_HAT_Y)
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
		android.util.Log.i("HudNav", "stick/hat dir=$dir x=$x y=$y src=${event.source}")
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
		title = { androidx.compose.material3.Text(if(initial.isEmpty()) "Add Manual Host" else "Edit Host") },
		text = {
			com.chiaking.tv.ui.screens.TvTextField("Host Address (IP)", value) { value = it }
		},
		confirmButton = {
			androidx.compose.material3.TextButton(
				onClick = { if(value.isNotBlank()) onSave(value.trim()) }
			) { androidx.compose.material3.Text("Save") }
		},
		dismissButton = {
			androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel") }
		}
	)
}
