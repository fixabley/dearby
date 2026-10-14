package io.wid.dearby.domain

// 응답 401(adaptor/web/ApiExceptionHandler). 경우가 늘어도 하위 클래스를 만들지 않고 메시지만 달리 던진다
// detail은 API 사용자에게 보이는 문장, logMessage는 로그에만 남는 내부 정보(id 등). 생략하면 detail과 같다
class AuthenticationFailedException(val detail: String, logMessage: String = detail) : RuntimeException(logMessage)
