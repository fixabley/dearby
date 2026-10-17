package io.wid.dearby.admin.application

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.Job

// 직무 트리에서 펼칠 것이 있는지 보여 주는 하위 수와 상위 경로(최상위부터 바로 위까지)
data class JobListing(val job: Job, val childCount: Int, val ancestors: List<Pair<Id, String>>)
