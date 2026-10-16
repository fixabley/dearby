package io.wid.dearby.domain

// 응답 503(adaptor/web/ApiExceptionHandler). 이 환경에 설정이 없어 쓸 수 없는 기능
class ServiceUnavailableException(val detail: String, logMessage: String = detail) : RuntimeException(logMessage)
