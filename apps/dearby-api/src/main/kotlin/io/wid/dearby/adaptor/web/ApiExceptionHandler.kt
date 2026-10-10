package io.wid.dearby.adaptor.web

import io.wid.dearby.adaptor.security.InvalidPasskeyRequestException
import io.wid.dearby.adaptor.security.PasskeyRejectedException
import io.wid.dearby.application.UserNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(UserNotFoundException::class)
    fun userNotFound(e: UserNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다")

    @ExceptionHandler(PasskeyRejectedException::class)
    fun passkeyRejected(e: PasskeyRejectedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.message)

    @ExceptionHandler(InvalidPasskeyRequestException::class)
    fun invalidPasskeyRequest(e: InvalidPasskeyRequestException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
}
