package io.wid.dearby.adaptor.web.v1.guest

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.*

// guestToken은 세션을 새로 만든 201에만 있다
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GuestSaveResponse(val cardId: UUID, val status: String, val guestToken: String?)
