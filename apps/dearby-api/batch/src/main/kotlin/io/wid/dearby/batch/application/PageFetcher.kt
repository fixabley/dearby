package io.wid.dearby.batch.application

// 공식 페이지를 직접 받는다. https, allowedHosts에 정확히 일치하는 호스트, 크기·시간 제한 밖이면 PageFetchException
fun interface PageFetcher {

    fun fetch(url: String, allowedHosts: List<String>): FetchedPage
}
