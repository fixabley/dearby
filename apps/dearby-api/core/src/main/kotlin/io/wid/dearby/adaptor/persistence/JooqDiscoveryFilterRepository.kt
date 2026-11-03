package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.DISCOVERY_FILTER_CONFIG
import io.wid.dearby.application.discovery.DiscoveryFilterRepository
import io.wid.dearby.domain.discovery.DiscoveryFilterConfig
import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.OffsetDateTime

// 발견 필터 구성(admin-v1.md "발견 필터 구성"): discovery_filter_config 한 행(id = 1)의 config jsonb
@Repository
class JooqDiscoveryFilterRepository(private val dsl: DSLContext) : DiscoveryFilterRepository {

    private val json = JsonMapper.builder().findAndAddModules().build()

    override fun find(): DiscoveryFilterConfig? =
        dsl.select(DISCOVERY_FILTER_CONFIG.CONFIG).from(DISCOVERY_FILTER_CONFIG).where(DISCOVERY_FILTER_CONFIG.ID.eq(ROW_ID))
            .fetchOne()?.value1()?.data()?.let { json.readValue<DiscoveryFilterConfig>(it) }

    override fun save(config: DiscoveryFilterConfig, at: OffsetDateTime) {
        val value = JSONB.valueOf(json.writeValueAsString(config))
        dsl.insertInto(DISCOVERY_FILTER_CONFIG)
            .set(DISCOVERY_FILTER_CONFIG.ID, ROW_ID)
            .set(DISCOVERY_FILTER_CONFIG.CONFIG, value)
            .set(DISCOVERY_FILTER_CONFIG.UPDATED_AT, at)
            .onConflict(DISCOVERY_FILTER_CONFIG.ID).doUpdate()
            .set(DISCOVERY_FILTER_CONFIG.CONFIG, value)
            .set(DISCOVERY_FILTER_CONFIG.UPDATED_AT, at)
            .execute()
    }

    private companion object {
        const val ROW_ID = 1
    }
}
