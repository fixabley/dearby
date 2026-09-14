package io.fixabley.dearby.app.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.fixabley.dearby.app.data.NoticeSnapshot
import io.fixabley.dearby.entities.notice.api.NoticeStorageCodec
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import org.json.JSONArray

@Entity(tableName = "snapshot_manifest")
internal data class SnapshotManifest(@PrimaryKey val id: String = "current", val digest: String,
    val codecVersion: Int, val snapshotDate: String, val noticeIds: String, val organizationIds: String) {
    companion object {
        fun from(snapshot: NoticeSnapshot): SnapshotManifest {
            require(snapshot.notices.map { it.id }.distinct().size == snapshot.notices.size)
            require(snapshot.organizations.map { it.id }.distinct().size == snapshot.organizations.size)
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeInt(NoticeStorageCodec.VERSION); out.writeUTF(snapshot.contentHash); out.writeUTF(snapshot.snapshotDate)
                out.writeInt(snapshot.organizations.size)
                snapshot.organizations.forEach { out.writeUTF(it.id); out.writeUTF(it.name); out.writeBoolean(it.parentId != null); it.parentId?.let(out::writeUTF) }
                out.writeInt(snapshot.notices.size)
                snapshot.notices.forEach { val payload = NoticeStorageCodec.encode(it); out.writeInt(payload.size); out.write(payload) }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
            return SnapshotManifest(digest = digest, codecVersion = NoticeStorageCodec.VERSION, snapshotDate = snapshot.snapshotDate,
                noticeIds = JSONArray(snapshot.notices.map { it.id }).toString(), organizationIds = JSONArray(snapshot.organizations.map { it.id }).toString())
        }
    }
}

@Dao
internal interface SnapshotManifestDao {
    @Query("SELECT * FROM snapshot_manifest") fun all(): List<SnapshotManifest>
    @Upsert fun upsert(value: SnapshotManifest)
}
