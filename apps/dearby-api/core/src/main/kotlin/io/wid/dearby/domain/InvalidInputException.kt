package io.wid.dearby.domain

// 응답 400(adaptor/web/ApiExceptionHandler). 경우가 늘어도 하위 클래스를 만들지 않고 메시지만 달리 던진다
// detail은 API 사용자에게 보이는 문장, logMessage는 로그에만 남는 내부 정보(id 등). 생략하면 detail과 같다
// code는 클라이언트가 구분해야 하는 경우만(native-v1.md의 `CONSENT_VERSION`처럼) ProblemDetail의 code 속성으로 실린다
class InvalidInputException(val detail: String, logMessage: String = detail, val code: String? = null) : IllegalArgumentException(logMessage)
