package io.wid.dearby.batch.application

// 웹 검색으로 찾은 후보 페이지(https, 허용 호스트가 있으면 그 안의 주소만)
data class Discovery(val urls: List<String>, val usage: ModelUsage)
