package io.wid.dearby.domain.profile

// 인증자료 파일. DB(bytea)에 둔다
data class Evidence(val meta: EvidenceMeta, val data: ByteArray)
