package io.wid.dearby.batch.application

// 웹 검색으로 찾은 후보 페이지(허용 호스트 안의 https 주소만)
data class Discovery(val urls: List<String>, val usage: ModelUsage)
