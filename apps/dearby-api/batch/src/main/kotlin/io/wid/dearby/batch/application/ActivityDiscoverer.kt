package io.wid.dearby.batch.application

import io.wid.dearby.domain.ProgramSource

// 프로그램의 모집 공지가 있을 만한 공식 페이지를 찾는다. 구현은 adaptor/claude
fun interface ActivityDiscoverer {

    fun discover(source: ProgramSource, programName: String): Discovery
}
