package io.fixabley.dearby.app.data.cache

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import io.fixabley.dearby.entities.notice.api.NoticeRecord
import io.fixabley.dearby.entities.notice.api.NoticeDao
import io.fixabley.dearby.entities.organization.api.OrganizationRecord
import io.fixabley.dearby.entities.organization.api.OrganizationDao

@Database(entities = [NoticeRecord::class, OrganizationRecord::class, SnapshotManifest::class], version = 1, exportSchema = true)
internal abstract class NoticeCacheDatabase : RoomDatabase() {
    abstract fun notices(): NoticeDao
    abstract fun organizations(): OrganizationDao
    abstract fun manifest(): SnapshotManifestDao

    companion object {
        /** Called on IO only. Never allow main-thread queries or destructive recovery. */
        fun open(context: Context, name: String = "dearby-notice-cache-v1.db"): NoticeCacheDatabase =
            Room.databaseBuilder(context.applicationContext, NoticeCacheDatabase::class.java, name)
                .openHelperFactory { configuration ->
                    val callback = configuration.callback
                    FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                        .name(configuration.name)
                        .callback(object : SupportSQLiteOpenHelper.Callback(callback.version) {
                            override fun onConfigure(db: SupportSQLiteDatabase) = callback.onConfigure(db)
                            override fun onCreate(db: SupportSQLiteDatabase) = callback.onCreate(db)
                            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = callback.onUpgrade(db, oldVersion, newVersion)
                            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = callback.onDowngrade(db, oldVersion, newVersion)
                            override fun onOpen(db: SupportSQLiteDatabase) = callback.onOpen(db)
                            override fun onCorruption(db: SupportSQLiteDatabase) { throw SQLiteDatabaseCorruptException("Notice cache is corrupt; preserving files") }
                        }).build())
                }.build()
    }
}
