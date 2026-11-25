package io.github.takosasidev.altroster.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * 版を上げるときは、必ず Migration を書いて [MIGRATIONS] に足す。
 * fallbackToDestructiveMigration は使わない(記録が黙って消えるため)。
 * スキーマの JSON は app/schemas/ に書き出して git で管理する。
 */
@Database(
    entities = [
        GameTitleEntity::class,
        GameAccountEntity::class,
        StaminaResourceEntity::class,
        GameEventEntity::class,
        DailyTaskEntity::class,
        TaskCheckInEntity::class,
        GachaLogEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rosterDao(): RosterDao

    companion object {
        const val VERSION = 1
        const val NAME = "altroster.db"

        /** 版 1 が最初なので、まだ 1 本も無い。版 2 を作るときに MIGRATION_1_2 をここへ足す。 */
        val MIGRATIONS: Array<Migration> = emptyArray()

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
                .also { instance = it }
        }
    }
}
