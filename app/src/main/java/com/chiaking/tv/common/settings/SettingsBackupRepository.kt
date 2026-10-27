package com.chiaking.tv.common.settings

import androidx.room.withTransaction
import com.chiaking.tv.common.db.AppDatabase
import com.chiaking.tv.common.db.ManualHostImport
import com.chiaking.tv.common.db.RegisteredHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Maps portable backup DTOs to/from database records; neither layer knows the other format. */
class SettingsBackupRepository(private val database: AppDatabase) {
    suspend fun export(): String = withContext(Dispatchers.IO) {
        val settings = database.withTransaction {
            val registered = database.registeredHostDao().getAllOnce()
            val manual = database.manualHostDao().getAllOnce()
            SerializedSettings(registered.map {
                SerializedRegisteredHost(it.target, it.apSsid, it.apBssid, it.apKey, it.apName,
                    it.serverMac, it.serverNickname, it.rpRegistKey, it.rpKeyType, it.rpKey)
            }, manual.map { host ->
                SerializedManualHost(host.host, registered.firstOrNull { it.id == host.registeredHost }?.serverMac)
            })
        }
        exportSettings(settings)
    }

    suspend fun restore(settings: SerializedSettings, replace: Boolean) = withContext(Dispatchers.IO) {
        val registered = settings.registeredHosts.map {
            RegisteredHost(target = it.target, apSsid = it.apSsid, apBssid = it.apBssid,
                apKey = it.apKey, apName = it.apName, serverMac = it.serverMac,
                serverNickname = it.serverNickname, rpRegistKey = it.rpRegistKey,
                rpKeyType = it.rpKeyType, rpKey = it.rpKey)
        }
        database.importDao().importAll(registered,
            settings.manualHosts.map { ManualHostImport(it.host, it.serverMac) }, replace)
    }
}
