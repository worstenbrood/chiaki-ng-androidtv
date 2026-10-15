// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui

import com.chiaking.tv.session.StreamSession
import com.metallic.chiaki.lib.RegistInfo

sealed class Screen
object ScreenHome: Screen()
object ScreenSettings: Screen()
class ScreenRegist(val host: String?, val ps5: Boolean?): Screen()
class ScreenRegistExecute(val info: RegistInfo, val assignManualHostId: Long?): Screen()
class ScreenStream(val session: StreamSession): Screen()
object ScreenLogs: Screen()
object ScreenManageHosts: Screen()
