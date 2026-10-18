package com.dearby.nativeapp.shared.lib

/** Index letters in display order: 초성 ㄱ…ㅎ, A…Z, then `#` (contract "받은 명함 목록 표시"). */
val NAME_INDEX_ORDER: List<String> = listOf("ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ") + ('A'..'Z').map { it.toString() } + "#"

// 19 initial consonants of a Hangul syllable, 된소리 folded into the base consonant.
private val INITIALS = listOf("ㄱ", "ㄱ", "ㄴ", "ㄷ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅂ", "ㅅ", "ㅅ", "ㅇ", "ㅈ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")

/** The index letter for a name: Hangul syllable → 초성, Latin → upper-case letter, anything else (or blank) → `#`. */
fun nameIndexKey(name: String): String {
    val first = name.trim().firstOrNull() ?: return "#"
    val code = first.code
    if (code in 0xAC00..0xD7A3) return INITIALS[(code - 0xAC00) / 588]
    if (first in 'a'..'z' || first in 'A'..'Z') return first.uppercaseChar().toString()
    return "#"
}

/** Sort position of an index letter (unknown letters sort last). */
fun nameIndexRank(key: String): Int = NAME_INDEX_ORDER.indexOf(key).let { if (it < 0) NAME_INDEX_ORDER.size else it }
