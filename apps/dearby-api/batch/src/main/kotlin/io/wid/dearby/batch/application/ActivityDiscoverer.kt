package io.wid.dearby.batch.application

import io.wid.dearby.domain.CollectionTarget

// 프로그램의 모집 공지가 있을 만한 페이지를 찾는다. 허용 호스트가 있으면 그 안에서만, 없으면 웹 전체에서. 구현은 adaptor/claude
fun interface ActivityDiscoverer {

    fun discover(target: CollectionTarget): Discovery
}
