// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE registered_host ADD COLUMN psn_account_id TEXT")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2)
{
	override fun migrate(database: SupportSQLiteDatabase)
	{
		database.execSQL("ALTER TABLE registered_host ADD target INTEGER NOT NULL DEFAULT 1000")
		database.execSQL("CREATE TABLE `new_registered_host` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `target` INTEGER NOT NULL, `ap_ssid` TEXT, `ap_bssid` TEXT, `ap_key` TEXT, `ap_name` TEXT, `server_mac` INTEGER NOT NULL, `server_nickname` TEXT, `rp_regist_key` BLOB NOT NULL, `rp_key_type` INTEGER NOT NULL, `rp_key` BLOB NOT NULL)");
		database.execSQL("INSERT INTO `new_registered_host` SELECT `id`, `target`, `ap_ssid`, `ap_bssid`, `ap_key`, `ap_name`, `ps4_mac`, `ps4_nickname`, `rp_regist_key`, `rp_key_type`, `rp_key` FROM `registered_host`")
		database.execSQL("DROP TABLE registered_host")
		database.execSQL("ALTER TABLE new_registered_host RENAME TO registered_host")
	}
}

val MIGRATION_2_3 = object : Migration(2, 3)
{
	override fun migrate(database: SupportSQLiteDatabase)
	{
		database.execSQL("ALTER TABLE registered_host ADD rp_pin TEXT")
	}
}
