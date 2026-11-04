package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.ProfileHistoriesRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.*
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.*
import org.jooq.DSLContext

// profile_histories 행 → History. 역할 이름과 귀속 단위(조직·프로그램·활동, 상위 단위까지)와 인증자료 목록은 한 번에 모아 읽는다.
// 역할·단위가 지워졌으면(role_id는 FK set null, owner_id는 논리키) 행에 복사해 둔 role_name·owner_name으로 표시한다. 프로필·관리자 검토 저장소가 함께 쓴다
class HistoryRows(private val dsl: DSLContext) {

    fun load(rows: List<ProfileHistoriesRecord>): List<History> {
        if (rows.isEmpty()) return emptyList()
        val roles = names(ROLES.ID, ROLES.NAME, ROLES, rows.mapNotNull { it.roleId })
        // 귀속 단위에서 상위 단위를 따라 올라간다: 활동 → 프로그램 → 조직
        val activities = dsl.select(ACTIVITIES.ID, ACTIVITIES.NAME, ACTIVITIES.PROGRAM_ID).from(ACTIVITIES)
            .where(ACTIVITIES.ID.`in`(ownerIds(rows, RoleOwnerKind.ACTIVITY))).fetch()
            .associate { it.value1()!! to Pair(it.value2()!!, it.value3()!!) }
        val programs = dsl.select(PROGRAMS.ID, PROGRAMS.NAME, PROGRAMS.ORGANIZATION_ID).from(PROGRAMS)
            .where(PROGRAMS.ID.`in`(ownerIds(rows, RoleOwnerKind.PROGRAM) + activities.values.map { it.second })).fetch()
            .associate { it.value1()!! to Pair(it.value2()!!, it.value3()!!) }
        val organizations = names(ORGANIZATIONS.ID, ORGANIZATIONS.NAME, ORGANIZATIONS, ownerIds(rows, RoleOwnerKind.ORGANIZATION) + programs.values.map { it.second })
        val evidence = dsl.select(HISTORY_EVIDENCE.ID, HISTORY_EVIDENCE.HISTORY_ID, HISTORY_EVIDENCE.FILE_NAME, HISTORY_EVIDENCE.CONTENT_TYPE, HISTORY_EVIDENCE.SIZE, HISTORY_EVIDENCE.UPLOADED_AT)
            .from(HISTORY_EVIDENCE).where(HISTORY_EVIDENCE.HISTORY_ID.`in`(rows.map { it.id!! })).orderBy(HISTORY_EVIDENCE.UPLOADED_AT)
            .fetch().groupBy({ it.value2()!! }) { EvidenceMeta(it.value1()!!, it.value3()!!, it.value4()!!, it.value5()!!, it.value6()!!) }
        return rows.map {
            val kind = RoleOwnerKind.valueOf(it.ownerKind!!)
            val activity = it.ownerId.takeIf { kind == RoleOwnerKind.ACTIVITY }?.let { id -> activities[id]?.let { (name, _) -> ActivityInfoRef(id, name) } }
            val programId = if (kind == RoleOwnerKind.PROGRAM) it.ownerId else activity?.let { a -> activities.getValue(a.id).second }
            val program = programId?.let { id -> programs[id]?.let { (name, _) -> ActivityInfoRef(id, name) } }
            val organizationId = if (kind == RoleOwnerKind.ORGANIZATION) it.ownerId else program?.let { p -> programs.getValue(p.id).second }
            val organization = organizationId?.let { id -> organizations[id]?.let { name -> ActivityInfoRef(id, name) } }
            val liveOwner = when (kind) { RoleOwnerKind.ACTIVITY -> activity; RoleOwnerKind.PROGRAM -> program; RoleOwnerKind.ORGANIZATION -> organization }
            val role = it.roleId?.let { id -> roles[id]?.let { name -> ActivityInfoRef(id, name) } }
            History(
                id = it.id!!,
                role = role,
                roleName = role?.name ?: it.roleName ?: "",
                owner = RoleOwner(kind, it.ownerId, liveOwner?.name ?: it.ownerName ?: ""),
                organization = organization,
                program = program,
                activity = activity,
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

    private fun ownerIds(rows: List<ProfileHistoriesRecord>, kind: RoleOwnerKind): List<Id> =
        rows.filter { it.ownerKind == kind.name }.mapNotNull { it.ownerId }

    private fun <R : org.jooq.Record> names(idField: org.jooq.TableField<R, Long?>, nameField: org.jooq.TableField<R, String?>, table: org.jooq.Table<R>, ids: List<Id>): Map<Id, String> {
        if (ids.isEmpty()) return emptyMap()
        return dsl.select(idField, nameField).from(table).where(idField.`in`(ids.toSet())).fetch().associate { it.value1()!! to it.value2()!! }
    }
}
