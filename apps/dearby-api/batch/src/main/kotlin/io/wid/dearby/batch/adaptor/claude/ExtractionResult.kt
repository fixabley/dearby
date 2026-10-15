package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.batch.application.ExtractedActivity

// CollectionPrompts.EXTRACT_SCHEMA 형태의 응답
data class ExtractionResult(val activities: List<ExtractedActivity>)
