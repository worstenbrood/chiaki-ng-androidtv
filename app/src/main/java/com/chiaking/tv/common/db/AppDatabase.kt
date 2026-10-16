// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.db

import android.content.Context
import androidx.room.*

@Database(
	version = 3,
	entities = [RegisteredHost::class, ManualHost::class],
	exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase()
{
	abstract fun registeredHostDao(): RegisteredHostDao
	abstract fun manualHostDao(): ManualHostDao
	abstract fun importDao(): ImportDao
}

private var database: AppDatabase? = null
fun getDatabase(context: Context): AppDatabase
{
	val currentDb = database
	if(currentDb != null)
		return currentDb
	val db = Room.databaseBuilder(
		context.applicationContext,
		AppDatabase::class.java,
		"chiaki")
		.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
		.build()
	database = db
	return db
}
