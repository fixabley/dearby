package io.wid.dearby.domain.profile

import io.wid.dearby.domain.Id

// 카탈로그 항목(조직·프로그램·활동·역할) 참조. 이름은 읽을 때의 카탈로그 이름
data class CatalogRef(val id: Id, val name: String)
