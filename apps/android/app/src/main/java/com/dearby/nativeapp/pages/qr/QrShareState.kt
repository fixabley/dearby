package com.dearby.nativeapp.pages.qr

enum class QrSharePhase { SIGNED_OUT, NO_CARD, LOADING, READY, FAILED }
/** One of the account's published cards as a tile on the QR tab. */
data class QrCardChoice(val id: String, val title: String, val subtitle: String)
/** The QR tab's real share: the chosen card's `<web>/s/<id>` link, or why there is none yet. */
data class QrShareState(
    val phase: QrSharePhase = QrSharePhase.LOADING, val name: String = "", val job: String = "",
    val url: String? = null, val error: String? = null,
    val cards: List<QrCardChoice> = emptyList(), val selectedCardId: String? = null, val activityIds: Set<String> = emptySet(),
)
