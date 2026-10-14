package io.wid.dearby.batch.application

// 수집 엔진을 지금 쓸 수 있는지. 쓸 수 없으면 이유를 돌려주고, 수집은 실행하지 않는다
fun interface CollectionReadiness {

    fun problem(): String?
}
