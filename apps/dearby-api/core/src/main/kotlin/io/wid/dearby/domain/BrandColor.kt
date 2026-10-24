package io.wid.dearby.domain

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

// 로고 대표색(native-v1.md "맞춤 대기열" 커버가 없을 때 그라데이션): 투명·흰색·검정·회색을 뺀 픽셀을 4비트씩 묶어 가장 많은 묶음의 평균색. 읽을 수 없거나 없으면 null
object BrandColor {
    private const val SAMPLE = 64
    private const val MIN_ALPHA = 128
    private const val LIGHT = 235
    private const val DARK = 25
    private const val MIN_CHROMA = 24

    init {
        ImageIO.scanForPlugins() // 실행 jar 안의 WebP 읽기 플러그인을 찾게 한다
    }

    fun of(data: ByteArray): String? {
        val image = runCatching { ImageIO.read(ByteArrayInputStream(data)) }.getOrNull() ?: return null
        val step = maxOf(1, maxOf(image.width, image.height) / SAMPLE)
        val buckets = mutableMapOf<Int, IntArray>() // 묶음 → [수, r합, g합, b합]
        for (y in 0 until image.height step step) for (x in 0 until image.width step step) {
            val argb = image.getRGB(x, y)
            val a = argb ushr 24
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            if (a < MIN_ALPHA || min > LIGHT || max < DARK || max - min < MIN_CHROMA) continue
            val sums = buckets.getOrPut(((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)) { IntArray(4) }
            sums[0]++; sums[1] += r; sums[2] += g; sums[3] += b
        }
        val top = buckets.values.maxByOrNull { it[0] } ?: return null
        return "#%02X%02X%02X".format(top[1] / top[0], top[2] / top[0], top[3] / top[0])
    }
}
