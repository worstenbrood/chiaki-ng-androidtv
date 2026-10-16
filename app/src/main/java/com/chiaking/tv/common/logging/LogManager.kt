// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.logging

import android.content.Context
import java.io.File
import java.util.Date

val fileProviderAuthority = "com.chiaking.tv.fileprovider"
private const val baseDirName = "session_logs" // must be in sync with filepaths.xml
private const val keepLogFilesCount = 5

class LogManager(context: Context)
{
	val baseDir = File(context.filesDir, baseDirName).also {
		it.mkdirs()
	}

	val files: List<LogFile> get() =
		(baseDir.list { _, s -> s.matches(LOG_FILE_REGEX) }?.toList() ?: listOf()).mapNotNull {
			LogFile.fromFilename(this, it)
		}.sortedByDescending {
			it.date
		}

	fun createNewFile(): LogFile
	{
		val currentFiles = files
		if(currentFiles.size > keepLogFilesCount)
		{
			currentFiles.subList(keepLogFilesCount, currentFiles.size).forEach {
				it.file.delete()
			}
		}

		val date = Date()
		val filename = "$LOG_FILE_PREFIX${LOG_DATE_FORMAT.format(date)}$LOG_FILE_POSTFIX"
		return LogFile.fromFilename(this, filename)!!
	}
}
