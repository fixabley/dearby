package io.wid.dearby.batch.application

// 페이지를 직접 받는다. https(허용 호스트에 적은 호스트는 http도), allowedHosts에 정확히 일치하는 호스트(비어 있으면 공인 주소의 모든 호스트), 크기·시간 제한 밖이면 PageFetchException
fun interface PageFetcher {

    fun fetch(url: String, allowedHosts: List<String>): FetchedPage
}
