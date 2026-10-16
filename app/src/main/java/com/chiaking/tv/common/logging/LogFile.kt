// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.logging

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val LOG_FILE_PREFIX = "chiaki_session_"
internal const val LOG_FILE_POSTFIX = ".log"
internal val LOG_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US)
internal val LOG_FILE_REGEX = Regex("$LOG_FILE_PREFIX(.*)$LOG_FILE_POSTFIX")

class LogFile private constructor(val logManager: LogManager, val filename: String)
{
	val date = LOG_FILE_REGEX.matchEntire(filename)?.groupValues?.get(1)?.let {
		LOG_DATE_FORMAT.parse(it)
	} ?: throw IllegalArgumentException()

	val file get() = File(logManager.baseDir, filename)

	companion object
	{
		fun fromFilename(logManager: LogManager, filename: String) = try { LogFile(logManager, filename) } catch(e: IllegalArgumentException) { null }
	}
}
