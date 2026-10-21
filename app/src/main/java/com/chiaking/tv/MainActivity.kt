// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv

import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.chiaking.tv.common.*
import com.chiaking.tv.common.db.*
import com.chiaking.tv.common.settings.*
import com.chiaking.tv.common.logging.*
import com.chiaking.tv.connect.LocalConnectController
import com.chiaking.tv.connect.RemoteConnectController
import com.chiaking.tv.connect.WakeConnectController
import com.chiaking.tv.discovery.DiscoveryManager
import com.chiaking.tv.session.AwayController
import com.chiaking.tv.session.StreamManager
import com.chiaking.tv.session.StreamStateConnected
import com.chiaking.tv.session.StreamStateDisconnecting
import com.chiaking.tv.ui.components.dialogs.ImportSettingsDialog
import com.chiaking.tv.ui.navigation.*
import com.chiaking.tv.ui.screens.*
import com.chiaking.tv.ui.screens.home.*
import com.chiaking.tv.ui.screens.settings.*
import com.chiaking.tv.ui.screens.stream.*
import com.chiaking.tv.ui.screens.logs.*
import com.chiaking.tv.ui.screens.regist.*
import com.chiaking.tv.ui.theme.ChiakiTvTheme
import com.chiaking.tv.ui.theme.TvBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity: ComponentActivity()
{
	companion object
	{
		const val EXTRA_AUTO_CONNECT_MAC = LocalConnectController.EXTRA_AUTO_CONNECT_MAC
	}

	private lateinit var preferences: Preferences
	private lateinit var database: AppDatabase
	private lateinit var logManager: LogManager
	private lateinit var wakeController: WakeConnectController
	private lateinit var remoteController: RemoteConnectController
	private lateinit var localController: LocalConnectController
	private lateinit var awayController: AwayController
	private lateinit var settingsTransfer: SettingsTransfer

	private val screenStack = mutableStateListOf<Screen>()

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

		preferences = Preferences(this)
		com.chiaking.tv.ui.theme.AppThemeState.theme = preferences.uiTheme
		database = getDatabase(this)
		logManager = LogManager(this)
		com.chiaking.tv.common.psn.CaBundle.ensure(this)
		com.chiaking.tv.common.psn.CaBundle.update(this)
		wakeController = WakeConnectController(lifecycleScope)
		remoteController = RemoteConnectController(this, lifecycleScope, preferences, database, logManager)
		remoteController.onStreamReady = { screenStack.add(ScreenStream(it)) }
		localController = LocalConnectController(this, lifecycleScope, database, preferences)
		localController.onConnectReady = { reg, found ->
			if(screenStack.lastOrNull() is ScreenHome)
				startStream(DiscoveredDisplayHost(reg, found))
		}
		localController.onWakeRequest = { discoveryManager, host, message ->
			if(screenStack.lastOrNull() is ScreenHome)
				wakeupAndConnect(discoveryManager, host, message)
		}
		localController.onRemoteConnectRequest = { device -> remoteController.connect(device) }
		awayController = AwayController(lifecycleScope, preferences)
		settingsTransfer = SettingsTransfer(this, database)
		screenStack.add(ScreenHome)
		StreamManager.session?.let { screenStack.add(ScreenStream(it)) }

		setContent {
			ChiakiTvTheme {
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = TvBackground
				) {
					Box {
						Navigation()
						ImportSettingsDialog(settingsTransfer)
					}
				}
			}
		}

		lifecycleScope.launch {
			database.registeredHostDao().getAll().collect { hosts ->
				withContext(Dispatchers.IO) { TvChannel.sync(this@MainActivity, preferences, hosts) }
			}
		}
		localController.handleDeepLink(intent)
	}
	override fun onNewIntent(intent: Intent)
	{
		super.onNewIntent(intent)
		localController.handleDeepLink(intent)
	}

	@Composable
	private fun Navigation()
	{
		BackHandler(enabled = screenStack.size > 1)
		{
			if(screenStack.size > 1)
				screenStack.removeAt(screenStack.size - 1)
		}
		when(val screen = screenStack.last())
		{
			ScreenHome -> HomeRoute(database, preferences, wakeController, remoteController, localController,
				onStartStream = { startStream(it) },
				onWakeup = { discoveryManager, host -> wakeupAndConnect(discoveryManager, host) },
				onRemoteConnect = { remoteController.connect(it) },
				onRegist = { host, ps5 -> screenStack.add(ScreenRegist(host, ps5)) },
				onOpenSettings = { screenStack.add(ScreenSettings) })

			ScreenSettings -> SettingsRoute(preferences, database,
				onBack = { popScreen() },
				onShowLogs = { screenStack.add(ScreenLogs) },
				onManageHosts = { screenStack.add(ScreenManageHosts) },
				onExportSettings = { settingsTransfer.launchExport() },
				onImportSettings = { settingsTransfer.launchImport() })

			is ScreenRegist -> RegistScreen(
				initialHost = screen.host,
				initialPs5 = screen.ps5,
				onBack = { popScreen() },
				onStartRegist = { info, assignId ->
					screenStack.add(ScreenRegistExecute(info, assignId))
				}
			)

			is ScreenRegistExecute -> RegistExecuteRoute(database, screen.info, screen.assignManualHostId) {
				repeat(2) { if(screenStack.size > 1) screenStack.removeAt(screenStack.size - 1) }
			}

			is ScreenStream -> StreamRoute(screen.session, preferences, database, awayController,
				onQuit = { disconnectStream() },
				onSleep = {
					StreamManager.markDisplayHostStandby()
					disconnectStream()
				},
				onKeepPlaying = { keepPlaying() },
				onSleepAway = { sleepConsoleAway() })

			ScreenLogs -> LogsScreen(logManager) { popScreen() }

			ScreenManageHosts -> ManageHostsRoute(database, preferences) { popScreen() }
		}
	}

	private fun popScreen()
	{
		if(screenStack.size > 1)
			screenStack.removeAt(screenStack.size - 1)
	}

	private fun wakeupAndConnect(discoveryManager: DiscoveryManager, host: DisplayHost, message: String = getString(R.string.waking))
	{
		val banner = getString(R.string.wake_waiting, message, host.name ?: getString(R.string.console))
		wakeController.toggle(discoveryManager, host, banner) { ready ->
			val reg = host.registeredHost ?: return@toggle
			if(screenStack.lastOrNull() is ScreenHome)
				startStream(DiscoveredDisplayHost(reg, ready))
		}
	}

	private fun startStream(host: DisplayHost)
	{
		wakeController.cancel()
		awayController.cancel()
		awayController.dismissPrompt()
		val session = StreamManager.start(this, preferences, logManager, host) ?: return
		screenStack.add(ScreenStream(session))
	}

	private fun disconnectStream()
	{
		awayController.cancel()
		awayController.dismissPrompt()
		val screen = screenStack.lastOrNull() as? ScreenStream ?: return
		if(screen.session.disconnecting)
			return
		screen.session.disconnecting = true
		screen.session.markDisconnecting()
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
		awayController.cancel()
		if(awayController.sleepPrompt)
			return
		resumeStream()
	}

	private fun resumeStream()
	{
		if(screenStack.lastOrNull() !is ScreenStream)
			return
		StreamManager.resumeStream(preferences)
	}

	private fun keepPlaying()
	{
		awayController.dismissPrompt()
		resumeStream()
	}

	override fun onPause()
	{
		super.onPause()
		val session = StreamManager.session
		session?.stopMicCapture()
		if(session != null && !session.disconnecting && session.session != null &&
			screenStack.lastOrNull() is ScreenStream &&
			preferences.disconnectAction != Preferences.DisconnectAction.NOTHING)
		{
			session.setVolumePercent(0)
			awayController.arm { sleepConsoleAway() }
		}
		else
			session?.pause()
	}

	private fun sleepConsoleAway()
	{
		if(StreamManager.sleepConsoleLive())
		{
			disconnectStream()
			return
		}
		StreamManager.sleepConsoleViaReconnect(this, preferences, logManager)
		StreamManager.clear()
		if(screenStack.lastOrNull() is ScreenStream)
			screenStack.removeAt(screenStack.size - 1)
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
		val streamScreen = screenStack.lastOrNull() as? ScreenStream
		if(streamScreen != null)
		{
			val session = streamScreen.session
			if(event.keyCode == KeyEvent.KEYCODE_BACK)
			{
				if(session.hudVisible.value)
				{
					if(event.action == KeyEvent.ACTION_UP)
						session.hideHud()
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
		val streamScreen = screenStack.lastOrNull() as? ScreenStream
		if(streamScreen != null)
		{
			if(hudStickNavigate(event))
				return true
			// While the HUD is open, gamepad sticks go straight to the game (Compose would eat them)
			val session = streamScreen.session
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
		dispatchKeyEvent(KeyEvent(event.eventTime, event.eventTime, KeyEvent.ACTION_DOWN, dir, 0))
		dispatchKeyEvent(KeyEvent(event.eventTime, event.eventTime, KeyEvent.ACTION_UP, dir, 0))
		return true
	}
}
