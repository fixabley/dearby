package io.wid.dearby.adaptor.web.v1.catalog

data class CatalogPageResponse(val items: List<CatalogItemResponse>, val nextCursor: Long?)
