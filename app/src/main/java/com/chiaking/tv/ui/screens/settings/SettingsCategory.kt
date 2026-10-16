// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.chiaking.tv.R

internal enum class SettingsCategory(val titleRes: Int, val icon: ImageVector)
{
	STREAM(R.string.stream, Icons.Default.Stream),
	AUDIO(R.string.audio, Icons.Default.VolumeUp),
	MIC(R.string.microphone, Icons.Default.Mic),
	CONTROLLER(R.string.controller, Icons.Default.SportsEsports),
	HUD(R.string.category_hud, Icons.Default.Dashboard),
	GENERAL(R.string.general, Icons.Default.Settings),
	PSN(R.string.playstation_network, Icons.Default.AccountCircle),
	BACKUP(R.string.backup, Icons.Default.SaveAlt),
	ABOUT(R.string.about, Icons.Default.Info)
}
