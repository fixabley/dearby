package io.wid.dearby.batch.application

// 헤드리스 브라우저로 페이지를 실행한 뒤 본문을 읽는다(admin-v1.md "브라우저 렌더링"). 수집 설정 renderPages가 켜진 프로그램만 쓴다.
// 경계는 PageFetcher와 같다(https(허용 호스트면 http)·허용 호스트·사설 IP·3,000,000자·탐색 15초). 실패는 PageFetchException("렌더링 실패: …")
interface RenderingPageFetcher : AutoCloseable {

    fun fetch(url: String, allowedHosts: List<String>): FetchedPage

    // 프로그램 하나의 수집이 끝나면 브라우저를 내린다(다음에 쓰면 다시 띄운다)
    override fun close()
}
