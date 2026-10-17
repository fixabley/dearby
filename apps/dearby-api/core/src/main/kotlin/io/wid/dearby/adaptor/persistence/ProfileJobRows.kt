package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.ProfileJobsRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.JOBS
import io.wid.dearby.domain.profile.ActivityInfoRef
import io.wid.dearby.domain.profile.ApprovalStatus
import io.wid.dearby.domain.profile.ProfileJob
import org.jooq.DSLContext

// profile_jobs 행 → ProfileJob. 직무 이름은 한 번에 모아 읽는다. 프로필·관리자 검토 저장소가 함께 쓴다
class ProfileJobRows(private val dsl: DSLContext) {

    fun load(rows: List<ProfileJobsRecord>): List<ProfileJob> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.mapNotNull { it.jobId }
        val names = if (ids.isEmpty()) emptyMap() else dsl.select(JOBS.ID, JOBS.NAME).from(JOBS).where(JOBS.ID.`in`(ids)).fetch().associate { it.value1()!! to it.value2()!! }
        return rows.map {
            // 직무 행이 지워졌으면(논리키) 직접 입력처럼 이름만 남긴다
            val ref = it.jobId?.let { id -> names[id]?.let { name -> ActivityInfoRef(id, name) } }
            ProfileJob(it.id!!, ref, it.jobName ?: ref?.name, ApprovalStatus.valueOf(it.approvalStatus!!), it.approvalReason)
        }
    }
}
