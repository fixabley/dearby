package io.wid.dearby.auth.web

import tools.jackson.databind.JsonNode
import java.util.*

// credential은 WebAuthn 표준 JSON(RegistrationResponseJSON·AuthenticationResponseJSON)
data class PasskeyRequest(val challengeId: UUID, val credential: JsonNode)
