package io.wid.dearby.application

// 응답 상태는 이 네 타입으로만 정한다(adaptor/web/ApiExceptionHandler). 경우가 늘어도 하위 클래스를 만들지 않고 메시지만 달리 던진다.
// detail은 API 사용자에게 보이는 문장, logMessage는 로그에만 남는 내부 정보(id 등). logMessage를 생략하면 detail과 같다

// 없는 대상 → 404
class NotFoundException(val detail: String, logMessage: String = detail) : NoSuchElementException(logMessage)

// 형식·값이 틀린 요청 → 400. Kotlin require()나 라이브러리가 던지는 IllegalArgumentException은 여기 해당하지 않는다(500)
class InvalidInputException(val detail: String, logMessage: String = detail) : IllegalArgumentException(logMessage)

// 인증 실패 → 401. 맞는 Java 기본 예외가 없어 RuntimeException을 상속한다
class AuthenticationFailedException(val detail: String, logMessage: String = detail) : RuntimeException(logMessage)

// 인증은 됐지만 권한이 없음 → 403. 맞는 Java 기본 예외가 없어 RuntimeException을 상속한다.
// Spring Security의 AccessDeniedException(필터·메서드 보안)과는 별개로, 애플리케이션이 직접 판단해 던진다
class PermissionDeniedException(val detail: String, logMessage: String = detail) : RuntimeException(logMessage)
