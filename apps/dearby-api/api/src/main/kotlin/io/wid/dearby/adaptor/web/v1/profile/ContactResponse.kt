package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.Contact
import java.time.OffsetDateTime
import java.util.*

// kind는 소문자(phone·email·…). 프로필·명함이 같은 형태를 쓴다. verified는 서버가 인증 결과로만 켠다(native-v1.md "연락처 인증")
data class ContactResponse(val id: UUID, val kind: String, val label: String, val value: String, val verified: Boolean, val verifiedAt: OffsetDateTime?) {
    companion object {
        fun from(contact: Contact) = ContactResponse(contact.id, contact.kind.name.lowercase(), contact.label, contact.value, contact.verified, contact.verifiedAt)
    }
}
