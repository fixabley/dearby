package com.dearby.nativeapp.shared.storage

import androidx.room.*

@Entity(tableName = "documents") data class DocumentRecord(@PrimaryKey val key: String, val json: String)
@Entity(tableName = "guest_cards") data class GuestRecord(@PrimaryKey val cardId: String, val contextJson: String, val savedAt: String)
@Dao interface DearbyDao {
    @Query("SELECT * FROM documents WHERE `key` = :key") suspend fun document(key: String): DocumentRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(record: DocumentRecord)
    @Query("DELETE FROM documents WHERE `key` = :key") suspend fun removeDocument(key: String)
    @Query("DELETE FROM documents WHERE `key` LIKE 'account:%:profile'") suspend fun clearAccount()
    @Query("SELECT * FROM guest_cards ORDER BY savedAt DESC") suspend fun guests(): List<GuestRecord>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun saveGuest(record: GuestRecord)
    @Query("DELETE FROM guest_cards WHERE cardId IN (:ids)") suspend fun removeGuests(ids: Set<String>)
}
@Database(entities = [DocumentRecord::class, GuestRecord::class], version = 1, exportSchema = true)
abstract class DearbyDatabase : RoomDatabase() { abstract fun dao(): DearbyDao }
