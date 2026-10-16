package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.ProfileHistoriesRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.*
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.*
import org.jooq.DSLContext
import java.util.*

// profile_histories 행 → History. 카탈로그 이름(조직·프로그램·활동·역할)과 인증자료 목록은 한 번에 모아 읽는다.
// 프로필·관리자 검토 저장소가 함께 쓴다
class HistoryRows(private val dsl: DSLContext) {

    fun load(rows: List<ProfileHistoriesRecord>): List<History> {
        if (rows.isEmpty()) return emptyList()
        val organizations = names(ORGANIZATIONS.ID, ORGANIZATIONS.NAME, ORGANIZATIONS, rows.mapNotNull { it.organizationId })
        val programs = names(PROGRAMS.ID, PROGRAMS.NAME, PROGRAMS, rows.mapNotNull { it.programId })
        val activities = names(ACTIVITIES.ID, ACTIVITIES.NAME, ACTIVITIES, rows.mapNotNull { it.activityId })
        val roles = names(ROLES.ID, ROLES.NAME, ROLES, rows.mapNotNull { it.roleId })
        val evidence = dsl.select(HISTORY_EVIDENCE.ID, HISTORY_EVIDENCE.HISTORY_ID, HISTORY_EVIDENCE.FILE_NAME, HISTORY_EVIDENCE.CONTENT_TYPE, HISTORY_EVIDENCE.SIZE, HISTORY_EVIDENCE.UPLOADED_AT)
            .from(HISTORY_EVIDENCE).where(HISTORY_EVIDENCE.HISTORY_ID.`in`(rows.map { it.id!! })).orderBy(HISTORY_EVIDENCE.UPLOADED_AT)
            .fetch().groupBy({ it.value2()!! }) { EvidenceMeta(it.value1()!!, it.value3()!!, it.value4()!!, it.value5()!!, it.value6()!!) }
        return rows.map {
            History(
                id = it.id!!,
                kind = HistoryKind.valueOf(it.kind!!),
                // 카탈로그 행이 지워졌으면(논리키) 직접 입력처럼 이름만 남긴다
                organization = ref(it.organizationId, organizations),
                organizationName = it.organizationName ?: ref(it.organizationId, organizations)?.name,
                program = ref(it.programId, programs),
                programName = it.programName ?: ref(it.programId, programs)?.name,
                activity = ref(it.activityId, activities),
                activityName = it.activityName ?: ref(it.activityId, activities)?.name,
                role = ref(it.roleId, roles),
                roleName = it.roleName ?: ref(it.roleId, roles)?.name,
                startDate = it.startDate!!,
                endDate = it.endDate,
                description = it.description!!,
                approval = ApprovalStatus.valueOf(it.approvalStatus!!),
                approvalReason = it.approvalReason,
                verification = VerificationStatus.valueOf(it.verificationStatus!!),
                evidence = evidence[it.id].orEmpty(),
            )
        }
    }

    private fun ref(id: Id?, names: Map<Id, String>): CatalogRef? = id?.let { names[it]?.let { name -> CatalogRef(it, name) } }

    private fun <R : org.jooq.Record> names(idField: org.jooq.TableField<R, Long?>, nameField: org.jooq.TableField<R, String?>, table: org.jooq.Table<R>, ids: List<Id>): Map<Id, String> {
        if (ids.isEmpty()) return emptyMap()
        return dsl.select(idField, nameField).from(table).where(idField.`in`(ids.toSet())).fetch().associate { it.value1()!! to it.value2()!! }
    }

    companion object {
        fun historyIds(rows: List<ProfileHistoriesRecord>): List<UUID> = rows.map { it.id!! }
    }
}
