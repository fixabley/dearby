package io.wid.dearby.application.discovery

import io.wid.dearby.domain.discovery.DiscoveryFilterConfig
import java.time.OffsetDateTime

// 발견 필터 구성 저장소 포트(admin-v1.md "발견 필터 구성"). 구현은 adaptor/persistence. 한 행(id = 1)
interface DiscoveryFilterRepository {

    // 저장된 구성. 없으면 null(기본 구성을 쓴다)
    fun find(): DiscoveryFilterConfig?

    fun save(config: DiscoveryFilterConfig, at: OffsetDateTime)
}
