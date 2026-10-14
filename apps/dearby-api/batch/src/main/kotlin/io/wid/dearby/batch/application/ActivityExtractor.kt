package io.wid.dearby.batch.application

// 받아 온 페이지 본문에서 활동을 뽑는다. 구현은 adaptor/claude
fun interface ActivityExtractor {

    fun extract(page: FetchedPage, programName: String): Extraction
}
