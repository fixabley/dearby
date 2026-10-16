package io.wid.dearby.adaptor.web.v1.profile

import java.util.*

data class ContactRequest(val id: UUID, val kind: String, val label: String = "", val value: String)
