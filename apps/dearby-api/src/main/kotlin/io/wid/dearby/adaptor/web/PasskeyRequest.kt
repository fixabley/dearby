package io.wid.dearby.adaptor.web

import tools.jackson.databind.JsonNode
import java.util.*

// credential은 WebAuthn 표준 JSON(RegistrationResponseJSON·AuthenticationResponseJSON)
data class PasskeyRequest(val challengeId: UUID, val credential: JsonNode)
