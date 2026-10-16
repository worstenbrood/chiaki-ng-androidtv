// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants

/**
 * Samples this process's CPU time from /proc/self/stat. [getCpuUsage] returns the percent of
 * TOTAL capacity across all cores since the previous call (0.0–100.0).
 */
class CpuUsageMonitor
{
	private val clockSpeedHz = Os.sysconf(OsConstants._SC_CLK_TCK)
	val coreCount = Runtime.getRuntime().availableProcessors()

	private var lastAppCpuTicks = 0L
	private var lastSampleTimeMs = 0L

	init
	{
		reset()
	}

	fun reset()
	{
		lastAppCpuTicks = readAppCpuTicks()
		lastSampleTimeMs = SystemClock.elapsedRealtime()
	}

	private fun readAppCpuTicks(): Long
	{
		return try
		{
			val line = java.io.RandomAccessFile("/proc/self/stat", "r").use { it.readLine() }
				?: return 0L
			// comm (field 2) is parenthesized and may contain spaces — parse after the last ')'
			val fields = line.substringAfterLast(')').trim().split(Regex("\\s+"))
			// after comm: state=0 ... utime=11, stime=12
			if(fields.size < 13)
				0L
			else
				fields[11].toLong() + fields[12].toLong()
		}
		catch(e: Exception)
		{
			0L
		}
	}

	/** Percent of total CPU capacity (all cores) used since the last call; call once per sample window. */
	fun getCpuUsage(): Double
	{
		val currentAppCpuTicks = readAppCpuTicks()
		val currentTimeMs = SystemClock.elapsedRealtime()
		val appTicksDelta = currentAppCpuTicks - lastAppCpuTicks
		val timeDeltaMs = currentTimeMs - lastSampleTimeMs
		lastAppCpuTicks = currentAppCpuTicks
		lastSampleTimeMs = currentTimeMs
		if(timeDeltaMs <= 0 || clockSpeedHz <= 0)
			return 0.0
		val timeDeltaSec = timeDeltaMs / 1000.0
		val totalSystemTicksPossible = clockSpeedHz * timeDeltaSec * coreCount
		if(totalSystemTicksPossible <= 0)
			return 0.0
		return ((appTicksDelta * 100.0) / totalSystemTicksPossible).coerceIn(0.0, 100.0)
	}
}
