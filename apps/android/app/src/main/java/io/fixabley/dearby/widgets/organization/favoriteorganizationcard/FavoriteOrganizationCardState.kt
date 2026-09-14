package io.fixabley.dearby.widgets.organization.favoriteorganizationcard

internal data class FavoriteNoticeState(val id: String, val title: String, val classification: String)
internal data class FavoriteOrganizationCardState(val id: String, val name: String, val ancestorNames: List<String>, val notices: List<FavoriteNoticeState>)
