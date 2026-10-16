package io.wid.dearby.domain.profile

import io.wid.dearby.domain.ContactKind
import java.util.*

// id는 클라이언트가 만든 UUID. 명함에는 고른 연락처만 그대로 복사된다
data class Contact(val id: UUID, val kind: ContactKind, val label: String, val value: String)
