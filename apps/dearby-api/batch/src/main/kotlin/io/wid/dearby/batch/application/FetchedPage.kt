package io.wid.dearby.batch.application

// 직접 받아 온 공식 페이지. text는 태그를 뺀 본문, links는 페이지의 <a href> 절대 주소
data class FetchedPage(val url: String, val text: String, val links: Set<String>)
