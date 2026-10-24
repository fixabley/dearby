package io.wid.dearby.application.discovery

import kotlin.math.sqrt

// 벡터 계산(활동 수백 개라 메모리에서 한다). 모든 벡터는 같은 모델의 것만 넘긴다
object Vectors {

    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / (sqrt(na) * sqrt(nb))
    }

    // 가중 평균. 비었으면 null
    fun weightedMean(items: List<Pair<FloatArray, Double>>): FloatArray? {
        val total = items.sumOf { it.second }
        if (items.isEmpty() || total <= 0) return null
        val result = FloatArray(items.first().first.size)
        items.forEach { (v, w) -> for (i in result.indices) result[i] += (v[i] * w / total).toFloat() }
        return result
    }

    // 긍정 평균 − 부정 평균을 단위 길이로. 둘 다 없으면 null
    fun difference(positive: FloatArray?, negative: FloatArray?): FloatArray? {
        val base = positive ?: negative?.let { FloatArray(it.size) } ?: return null
        val result = FloatArray(base.size) { i -> base[i] - (negative?.get(i) ?: 0f) }
        val norm = sqrt(result.sumOf { (it * it).toDouble() })
        return if (norm == 0.0) null else FloatArray(result.size) { (result[it] / norm).toFloat() }
    }
}

// 받침에 따라 과/와(한글이 아니면 숫자 읽기, 그 밖은 와)
object Josa {
    private const val BATCHIM_DIGITS = "013678"

    fun withAnd(word: String): String {
        val last = word.trimEnd().lastOrNull() ?: return "와"
        val batchim = when (last) {
            in '가'..'힣' -> (last - '가') % 28 != 0
            in '0'..'9' -> last in BATCHIM_DIGITS
            else -> false
        }
        return if (batchim) "과" else "와"
    }
}
