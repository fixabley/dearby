package io.wid.dearby.application.auth

import tools.jackson.databind.JsonNode
import java.util.*

// options·credential은 WebAuthn 표준 JSON(base64url)
data class PasskeyOptions(val challengeId: UUID, val options: JsonNode)
