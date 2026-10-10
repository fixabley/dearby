package io.wid.dearby.adaptor.web

import io.wid.dearby.application.AuthenticationFailedException
import io.wid.dearby.application.InvalidInputException
import io.wid.dearby.application.NotFoundException
import io.wid.dearby.application.PermissionDeniedException
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

    // 4xx는 정상적인 요청 실패라 info로 남긴다
    private fun problem(status: HttpStatus, detail: String, e: RuntimeException): ProblemDetail {
        log.info("{} {}", status.value(), e.message)
        return ProblemDetail.forStatusAndDetail(status, detail)
    }
}
