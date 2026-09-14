package io.fixabley.dearby.entities.activitycatalog.api

import io.fixabley.dearby.entities.activitycatalog.model.ActivityCatalog

/** Synchronous sample supply boundary; no network policy is implied. */
internal fun interface CatalogProvider {
    fun load(): ActivityCatalog
}
