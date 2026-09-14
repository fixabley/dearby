package io.fixabley.dearby.core.data

import io.fixabley.dearby.core.model.ActivityCatalog

/** Synchronous sample supply boundary; no network policy is implied. */
fun interface CatalogProvider {
    fun load(): ActivityCatalog
}
