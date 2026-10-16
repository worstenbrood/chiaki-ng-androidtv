// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.home

import com.chiaking.tv.common.DisplayHost
import com.chiaking.tv.common.db.ManualHost
import com.metallic.chiaki.lib.HolepunchDevice

sealed class HomeAction
data class ConnectHost(val host: DisplayHost): HomeAction()
data class WakeupHost(val host: DisplayHost): HomeAction()
data class EditManualHost(val manualHost: ManualHost): HomeAction()
data class DeleteManualHost(val manualHost: ManualHost): HomeAction()
object RegisterNew: HomeAction()
data class RegisterHost(val host: String, val ps5: Boolean): HomeAction()
object AddManualHost: HomeAction()
object OpenSettings: HomeAction()
object ToggleDiscovery: HomeAction()
data class ConnectRemoteConsole(val device: HolepunchDevice): HomeAction()
object RefreshPsnDevices: HomeAction()
