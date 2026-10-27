// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.settings

import com.chiaking.tv.common.db.*

import com.chiaking.tv.common.settings.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.chiaking.tv.R
import kotlinx.coroutines.launch
import okio.buffer
import okio.source

/**
 * Owns the settings export/import document launchers and the pending-import confirmation state,
 * so the Activity only has to render the Replace/Merge dialog.
 */
class SettingsTransferController(private val activity: ComponentActivity, database: AppDatabase)
{
	private val backup = SettingsBackupRepository(database)

	var pendingImport by mutableStateOf<SerializedSettings?>(null)
		private set

	private val exportLauncher = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
		if(uri == null) return@registerForActivityResult
		activity.lifecycleScope.launch {
			val json = backup.export()
			activity.contentResolver.openOutputStream(uri)?.use { out ->
				out.write(json.toByteArray())
			}
		}
	}

	private val importLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if(uri == null) return@registerForActivityResult
		activity.lifecycleScope.launch {
			try {
				val settings = activity.contentResolver.openInputStream(uri)!!.source().buffer().use {
					parseSettingsImport(it)
				}
				pendingImport = settings
			} catch(e: Exception) {
				android.util.Log.e("SettingsTransferController", "Import failed", e)
				Toast.makeText(activity, activity.getString(R.string.import_failed, e.message), Toast.LENGTH_LONG).show()
			}
		}
	}

	fun launchExport()
	{
		exportLauncher.launch("chiaki-settings.json")
	}

	fun launchImport()
	{
		importLauncher.launch(arrayOf("application/json"))
	}

	fun dismissImport()
	{
		pendingImport = null
	}

	fun confirmImport(replace: Boolean)
	{
		val settings = pendingImport ?: return
		pendingImport = null
		activity.lifecycleScope.launch {
			try {
				backup.restore(settings, replace)
				Toast.makeText(activity, activity.getString(R.string.imported_count, settings.registeredHosts.size), Toast.LENGTH_SHORT).show()
			} catch(e: Exception) {
				android.util.Log.e("SettingsTransferController", "Import failed", e)
				Toast.makeText(activity, activity.getString(R.string.import_failed, e.message), Toast.LENGTH_LONG).show()
			}
		}
	}
}
