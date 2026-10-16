package io.wid.dearby.domain.profile

import io.wid.dearby.domain.Id

// 활동 정보 항목(조직·프로그램·활동·역할) 참조. 이름은 읽을 때의 활동 정보 이름
data class ActivityInfoRef(val id: Id, val name: String)
