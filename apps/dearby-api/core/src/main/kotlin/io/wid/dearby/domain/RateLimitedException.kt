package io.wid.dearby.domain

// 응답 429(adaptor/web/ApiExceptionHandler). 게스트 요청 제한
class RateLimitedException(val detail: String, logMessage: String = detail) : RuntimeException(logMessage)
