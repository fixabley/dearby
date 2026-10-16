package io.wid.dearby.auth.web

import io.wid.dearby.domain.AuthenticationFailedException
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.PermissionDeniedException
import io.wid.dearby.domain.ServiceUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// 응답에는 detail만 싣고 내부 메시지는 로그에만 남긴다
@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = problem(HttpStatus.NOT_FOUND, e.detail, e)

    @ExceptionHandler(InvalidInputException::class)
    fun invalidInput(e: InvalidInputException) = problem(HttpStatus.BAD_REQUEST, e.detail, e)

    @ExceptionHandler(AuthenticationFailedException::class)
    fun authenticationFailed(e: AuthenticationFailedException) = problem(HttpStatus.UNAUTHORIZED, e.detail, e)

    @ExceptionHandler(PermissionDeniedException::class)
    fun permissionDenied(e: PermissionDeniedException) = problem(HttpStatus.FORBIDDEN, e.detail, e)

    @ExceptionHandler(ServiceUnavailableException::class)
    fun serviceUnavailable(e: ServiceUnavailableException) = problem(HttpStatus.SERVICE_UNAVAILABLE, e.detail, e)

    // 정상적인 요청 실패(4xx)와 설정 없음(503)이라 info로 남긴다
    private fun problem(status: HttpStatus, detail: String, e: RuntimeException): ProblemDetail {
        log.info("{} {}", status.value(), e.message)
        return ProblemDetail.forStatusAndDetail(status, detail)
    }
}
