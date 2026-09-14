package io.fixabley.dearby.entities.notice.api

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.fixabley.dearby.entities.notice.model.NoticeModel

@Entity(tableName = "notices")
internal data class NoticeRecord(@PrimaryKey val id: String, val codecVersion: Int, val payload: ByteArray)

@Dao
internal interface NoticeDao {
    @Query("SELECT * FROM notices WHERE id = :id") fun find(id: String): NoticeRecord?
    @Upsert fun upsert(record: NoticeRecord)
    @Query("DELETE FROM notices") fun clear()
}

internal class RoomNoticeStore(private val dao: NoticeDao) : NoticeDiskStore {
    override fun find(id: String): NoticeModel? = dao.find(id)?.let {
        require(it.codecVersion == NoticeStorageCodec.VERSION) { "Unsupported notice record" }
        NoticeStorageCodec.decode(it.payload).also { value -> require(value.id == id) { "Notice payload ID mismatch" } }
    }
    override fun upsert(value: NoticeModel) = dao.upsert(NoticeRecord(value.id, NoticeStorageCodec.VERSION, NoticeStorageCodec.encode(value)))
}
