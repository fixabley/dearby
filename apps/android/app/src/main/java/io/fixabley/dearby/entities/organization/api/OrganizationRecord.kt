package io.fixabley.dearby.entities.organization.api

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.fixabley.dearby.entities.organization.model.OrganizationModel

@Entity(tableName = "organizations")
internal data class OrganizationRecord(@PrimaryKey val id: String, val name: String, val parentId: String?)

@Dao
internal interface OrganizationDao {
    @Query("SELECT * FROM organizations WHERE id = :id") fun find(id: String): OrganizationRecord?
    @Upsert fun upsert(record: OrganizationRecord)
    @Query("DELETE FROM organizations") fun clear()
}

internal class RoomOrganizationStore(private val dao: OrganizationDao) : OrganizationDiskStore {
    override fun find(id: String) = dao.find(id)?.let { OrganizationModel(it.id, it.name, it.parentId) }
    override fun upsert(value: OrganizationModel) = dao.upsert(OrganizationRecord(value.id, value.name, value.parentId))
}
