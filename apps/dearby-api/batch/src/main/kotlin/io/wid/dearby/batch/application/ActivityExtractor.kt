package io.wid.dearby.batch.application

import io.wid.dearby.domain.collection.WatchEntry

// 받아 온 페이지 본문에서 활동을 뽑는다. 구현은 adaptor/claude. watchlist는 조심 목록(피할 이름·다른 표기) 힌트
fun interface ActivityExtractor {

    fun extract(page: FetchedPage, programName: String, watchlist: List<WatchEntry>): Extraction
}
