package io.wid.dearby.adaptor.web

import io.wid.dearby.application.UserNotFoundException
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import kotlin.test.Test

class ApiExceptionHandlerTest {

    @RestController
    class ThrowingController {
        @GetMapping("/users/missing")
        fun missing(): Nothing = throw UserNotFoundException(UUID.randomUUID())
    }

    private val mvc = MockMvcBuilders.standaloneSetup(ThrowingController())
        .setControllerAdvice(ApiExceptionHandler())
        .build()

    @Test
    fun `사용자가 없으면 404 ProblemDetail`() {
        mvc.get("/users/missing").andExpect {
            status { isNotFound() }
            jsonPath("$.status") { value(404) }
        }
    }
}
