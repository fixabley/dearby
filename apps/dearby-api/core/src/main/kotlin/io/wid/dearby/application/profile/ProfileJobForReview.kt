package io.wid.dearby.application.profile

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.ProfileJob

// 관리자 직무 승인 화면용. 화면에는 profileName만 보여 준다(계정 id는 싣지 않는다)
data class ProfileJobForReview(val entry: ProfileJob, val userId: UserId, val profileName: String)
