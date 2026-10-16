package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.Contact
import java.util.*

// kind는 소문자(phone·email·…). 프로필·명함이 같은 형태를 쓴다
data class ContactResponse(val id: UUID, val kind: String, val label: String, val value: String) {
    companion object {
        fun from(contact: Contact) = ContactResponse(contact.id, contact.kind.name.lowercase(), contact.label, contact.value)
    }
}
