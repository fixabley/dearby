package io.wid.dearby.batch.application

import io.wid.dearby.domain.collection.PreviousEdition
import io.wid.dearby.domain.collection.WatchEntry

// 받아 온 페이지 본문에서 활동을 뽑는다. 구현은 adaptor/claude.
// watchlist는 조심 목록(피할 이름·다른 표기) 힌트, previous는 이전 기수의 대상자 조건·대상 원문 힌트(admin-v1.md "이전 기수 정보 자동완성")
fun interface ActivityExtractor {

    fun extract(page: FetchedPage, programName: String, watchlist: List<WatchEntry>, previous: PreviousEdition?): Extraction
}
