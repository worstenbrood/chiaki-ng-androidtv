// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.stream

internal data class HudStats(
	val fps: Double,
	val kbps: Double,
	val framesLost: Long,
	val packetLossPct: Double,
	val rttMs: Long,
	val cpuAppPct: Double,
	val cpuCores: Double,
	val cpuCoreCount: Int
)
