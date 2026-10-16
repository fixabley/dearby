package io.wid.dearby.domain

// 응답 409(adaptor/web/ApiExceptionHandler). 버전 불일치, 용량 초과 같은 상태 충돌
class ConflictException(val detail: String, logMessage: String = detail) : RuntimeException(logMessage)
