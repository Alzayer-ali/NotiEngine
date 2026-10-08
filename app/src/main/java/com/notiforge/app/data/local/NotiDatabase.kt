package com.notiforge.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TemplateEntity::class, EventLogEntity::class],
    version = 3,
    exportSchema = false
)
abstract class NotiDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun eventLogDao(): EventLogDao

    companion object {
        @Volatile
        private var INSTANCE: NotiDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE templates 
                    SET name = REPLACE(REPLACE(name, 'Quick Noe', 'Quick Note'), 'quick noe', 'Quick Note'),
                        description = REPLACE(description, 'Quick Noe', 'Quick Note'),
                        defaultTitle = REPLACE(defaultTitle, 'Quick Noe', 'Quick Note')
                    WHERE name LIKE '%Quick Noe%' OR description LIKE '%Quick Noe%' OR defaultTitle LIKE '%Quick Noe%'
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE templates
                    SET name = 'Quick Note'
                    WHERE slug = 'quick_note' AND isPreset = 1
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE templates ADD COLUMN blocksJson TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): NotiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotiDatabase::class.java,
                    "notiengine.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
