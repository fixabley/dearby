package com.dearby.nativeapp.entities.activity.model

/**
 * One entry of the operator-managed tag dictionary (contract "태그(얻을 수 있는 것)", 2026-10-19): what a participant gains,
 * e.g. 창업·협업·멘토링. [id] is the API's number as text; [nameEn] may be null until an operator fills it.
 */
data class Tag(val id: String, val name: String, val nameEn: String?)
