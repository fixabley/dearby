package io.wid.dearby.admin.application

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.domain.curation.ActivityCuration

data class AdminActivity(val listing: ActivityListing, val curation: ActivityCuration)
