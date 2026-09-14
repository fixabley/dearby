package io.fixabley.dearby.entities.noticecatalog.api

import io.fixabley.dearby.entities.noticecatalog.model.NoticeCatalog

/** Synchronous sample supply boundary; no network policy is implied. */
internal fun interface CatalogProvider {
    fun load(): NoticeCatalog
}
