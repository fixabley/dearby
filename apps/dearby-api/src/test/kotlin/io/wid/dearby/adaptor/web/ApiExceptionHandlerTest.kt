package io.wid.dearby.adaptor.web

import io.wid.dearby.domain.AuthenticationFailedException
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.PermissionDeniedException
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ApiExceptionHandlerTest {

    @RestController
    class ThrowingController {
        @GetMapping("/not-found")
        fun notFound(): Nothing = throw NotFoundException("사용자를 찾을 수 없습니다", "User not found: secret-id")

        @GetMapping("/invalid")
        fun invalid(): Nothing = throw InvalidInputException("형식이 올바르지 않습니다")

        @GetMapping("/unauthorized")
        fun unauthorized(): Nothing = throw AuthenticationFailedException("인증에 실패했습니다")

        @GetMapping("/forbidden")
        fun forbidden(): Nothing = throw PermissionDeniedException("권한이 없습니다")

        @GetMapping("/library-iae")
        fun libraryIae(): Nothing = throw IllegalArgumentException("internal")
    }

    private val mvc = MockMvcBuilders.standaloneSetup(ThrowingController())
        .setControllerAdvice(ApiExceptionHandler())
        .build()

    @Test
    fun `응답에는 detail만 싣고 로그 메시지는 싣지 않는다`() {
        mvc.get("/not-found").andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value("사용자를 찾을 수 없습니다") }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-id"))) }
        }
    }

    @Test
    fun `예외 타입으로 상태가 정해진다`() {
        mvc.get("/invalid").andExpect { status { isBadRequest() } }
        mvc.get("/unauthorized").andExpect { status { isUnauthorized() } }
        mvc.get("/forbidden").andExpect { status { isForbidden() } }
    }

    @Test
    fun `일반 IllegalArgumentException은 400으로 바꾸지 않는다`() {
        assertFailsWith<Exception> { mvc.get("/library-iae") }
    }
}
