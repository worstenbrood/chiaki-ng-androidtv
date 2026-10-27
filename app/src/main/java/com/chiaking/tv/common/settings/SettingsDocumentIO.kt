package com.chiaking.tv.common.settings

import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source

/** Opening, parsing and closing document-provider streams all happen off the UI thread. */
internal object SettingsDocumentIO {
    suspend fun read(open: () -> InputStream?): SerializedSettings = withContext(Dispatchers.IO) {
        val stream = open() ?: throw IOException("Unable to open backup for reading")
        stream.source().buffer().use { parseSettingsImport(it) }
    }

    suspend fun write(json: String, open: () -> OutputStream?) = withContext(Dispatchers.IO) {
        val stream = open() ?: throw IOException("Unable to open backup for writing")
        stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }
}
