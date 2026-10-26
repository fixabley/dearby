package io.wid.dearby.domain.profile

import io.wid.dearby.domain.ContactKind
import java.time.OffsetDateTime
import java.util.*

// id는 클라이언트가 만든 UUID. 명함에는 고른 연락처만 그대로 복사된다.
// verified는 서버가 인증 결과(GitHub 로그인·이메일 인증번호, native-v1.md "연락처 인증")로만 켠다. 값을 고치면 다시 false
data class Contact(
    val id: UUID,
    val kind: ContactKind,
    val label: String,
    val value: String,
    val verified: Boolean = false,
    val verifiedAt: OffsetDateTime? = null,
)
