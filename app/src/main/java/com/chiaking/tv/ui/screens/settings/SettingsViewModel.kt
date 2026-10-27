package com.chiaking.tv.ui.screens.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chiaking.tv.common.AudioDevices
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.db.RegisteredHost
import com.chiaking.tv.common.psn.PsnAccountRepository
import com.chiaking.tv.common.settings.Preferences
import com.chiaking.tv.ui.theme.AppThemeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Settings mutations and account data live here; panels only render state and issue actions. */
internal class SettingsViewModel(context: Context, database: AppDatabase, private val preferences: Preferences) : ViewModel() {
    private val accounts = PsnAccountRepository.get(context)
    private var accountState by mutableStateOf(accounts.state.value)
    var registeredHosts by mutableStateOf<List<RegisteredHost>>(emptyList())
        private set
    val psnAccounts get() = accountState.accounts
    val primaryAccountId get() = accountState.primaryId
    val psnLoggedIn get() = primaryAccountId != null
    private var profileJob: Job? = null

    init {
        viewModelScope.launch { accounts.state.collect { accountState = it } }
        viewModelScope.launch { database.registeredHostDao().getAll().collect { registeredHosts = it } }
    }

    fun signOut() = accounts.logout()
    fun removeAccount(rid: String) = accounts.removeAccount(rid)
    fun onCategoryChanged(category: SettingsCategory) {
        profileJob?.cancel()
        if(category == SettingsCategory.PSN) profileJob = viewModelScope.launch {
            for(account in accounts.accounts()) accounts.refreshProfile(account.rid)
        }
    }

    val bitrateAuto get() = preferences.bitrateAuto
    val audioDevices = AudioDevices.outputDevices(context)
    val micDevices = AudioDevices.inputDevices(context)
    var deviceIndex by mutableStateOf(AudioDevices.indexOfSavedOutput(audioDevices,
        preferences.audioDeviceName, preferences.audioDeviceId))
        private set
    fun nextAudioDevice() {
        deviceIndex = (deviceIndex + 1) % audioDevices.size
        preferences.audioDeviceId = audioDevices[deviceIndex].second
        preferences.audioDeviceName = audioDevices[deviceIndex].first
    }
    var micDeviceIndex by mutableStateOf(micDevices.indexOfFirst { it.first == preferences.micDeviceName }.coerceAtLeast(0))
        private set
    fun nextMicDevice() {
        micDeviceIndex = (micDeviceIndex + 1) % micDevices.size
        preferences.micDeviceName = micDevices[micDeviceIndex].first
    }
    private var autoConnectMac by preferenceState({ preferences.autoConnectMac }, { preferences.autoConnectMac = it })
    val autoConnectOptions get() = listOf<RegisteredHost?>(null) + registeredHosts
    val autoConnectIndex get() = registeredHosts.indexOfFirst { it.serverMac.toString() == autoConnectMac } + 1
    fun nextAutoConnectConsole() {
        val index = (autoConnectIndex + 1) % autoConnectOptions.size
        autoConnectMac = autoConnectOptions[index]?.serverMac?.toString().orEmpty()
    }

    private fun <T> preferenceState(read: () -> T, write: (T) -> Unit): ReadWriteProperty<Any?, T> =
        object : ReadWriteProperty<Any?, T> {
            private var value by mutableStateOf(read())
            override fun getValue(thisRef: Any?, property: KProperty<*>) = value
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                write(value)
                this.value = read()
            }
        }

    var resolution by preferenceState({ preferences.resolution }, { preferences.resolution = it })
    var fps by preferenceState({ preferences.fps }, { preferences.fps = it })
    var codec by preferenceState({ preferences.codec }, { preferences.codec = it })
    var bitrate by preferenceState({ preferences.bitrate }, { preferences.bitrate = it })
    var discovery by preferenceState({ preferences.discoveryEnabled }, { preferences.discoveryEnabled = it })
    var rumble by preferenceState({ preferences.rumbleEnabled }, { preferences.rumbleEnabled = it })
    var motion by preferenceState({ preferences.motionEnabled }, { preferences.motionEnabled = it })
    var haptics by preferenceState({ preferences.buttonHapticEnabled }, { preferences.buttonHapticEnabled = it })
    var swapCrossMoon by preferenceState({ preferences.swapCrossMoon }, { preferences.swapCrossMoon = it })
    var dpadAsController by preferenceState({ preferences.dpadAsController }, { preferences.dpadAsController = it })
    var logVerbose by preferenceState({ preferences.logVerbose }, { preferences.logVerbose = it })
    var volume by preferenceState({ preferences.audioVolume }, { preferences.audioVolume = it })
    var bufferMs by preferenceState({ preferences.audioBufferMs }, { preferences.audioBufferMs = it })
    var autoConnect by preferenceState({ preferences.autoConnectEnabled }, { preferences.autoConnectEnabled = it })
    var menuShortcut by preferenceState({ preferences.menuShortcut }, { preferences.menuShortcut = it })
    var hudItems by preferenceState({ preferences.hudItems }, { preferences.hudItems = it })
    var displayMode by preferenceState({ preferences.displayMode }, { preferences.displayMode = it })
    var disconnectAction by preferenceState({ preferences.disconnectAction }, { preferences.disconnectAction = it })
    var uiTheme by preferenceState({ preferences.uiTheme }, { preferences.uiTheme = it; AppThemeState.theme = it })
    var idrOnFec by preferenceState({ preferences.idrOnFecFailure }, { preferences.idrOnFecFailure = it })
    var micEnabled by preferenceState({ preferences.micEnabled }, { preferences.micEnabled = it })
    var micStartUnmuted by preferenceState({ preferences.micStartUnmuted }, { preferences.micStartUnmuted = it })
    var micGain by preferenceState({ preferences.micGain }, { preferences.micGain = it })
    var micAec by preferenceState({ preferences.micAec }, { preferences.micAec = it })
    var micNs by preferenceState({ preferences.micNs }, { preferences.micNs = it })
    var micAgc by preferenceState({ preferences.micAgc }, { preferences.micAgc = it })
    var micComplexity by preferenceState({ preferences.micComplexity }, { preferences.micComplexity = it })
    var micBandwidth by preferenceState({ preferences.micBandwidth }, { preferences.micBandwidth = it })
    var micPitchEnabled by preferenceState({ preferences.micPitchEnabled }, { preferences.micPitchEnabled = it })
    var micPitch by preferenceState({ preferences.micPitch }, { preferences.micPitch = it })
    var loginPin by preferenceState({ preferences.loginPin }, { preferences.loginPin = it })
}
