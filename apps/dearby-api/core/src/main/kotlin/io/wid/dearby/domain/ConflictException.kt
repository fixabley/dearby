package io.wid.dearby.domain

// 409. code는 클라이언트가 구분해야 하는 경우만(native-v1.md의 `OWN_CARD`처럼) ProblemDetail의 code 속성으로 실린다
class ConflictException(val detail: String, logMessage: String = detail, val code: String? = null) : RuntimeException(logMessage)
