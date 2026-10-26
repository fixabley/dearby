package io.wid.dearby.application.profile

import io.wid.dearby.domain.InvalidInputException
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

// 프로필 사진 다시 인코딩(native-v1.md "프로필 사진"): JDK ImageIO로 읽어 가운데 정사각형으로 자르고 한 변 SIDE 이하 JPEG로 쓴다.
// 픽셀만 새로 쓰므로 EXIF(위치 등)·ICC 같은 메타데이터는 남지 않는다
object ProfilePhotoImage {
    const val SIDE = 512
    const val MAX_INPUT_SIDE = 4096
    private const val QUALITY = 0.85f

    fun reencode(data: ByteArray): ByteArray {
        val source = decode(data)
        val crop = minOf(source.width, source.height)
        val side = minOf(crop, SIDE)
        val out = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.color = Color.WHITE // 투명 PNG는 흰 바탕
            g.fillRect(0, 0, side, side)
            val x = (source.width - crop) / 2
            val y = (source.height - crop) / 2
            g.drawImage(source, 0, 0, side, side, x, y, x + crop, y + crop, null)
        } finally {
            g.dispose()
        }
        val bytes = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            ImageIO.createImageOutputStream(bytes).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = QUALITY }
                writer.write(null, IIOImage(out, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return bytes.toByteArray()
    }

    // 크기를 먼저 읽어 너무 큰 이미지(압축 폭탄)는 풀지 않는다. 읽을 수 없는 형식(CMYK JPEG 등)은 400
    private fun decode(data: ByteArray): BufferedImage {
        ImageIO.createImageInputStream(data.inputStream()).use { input ->
            val reader = input?.let { ImageIO.getImageReaders(it) }?.takeIf { it.hasNext() }?.next() ?: throw unreadable()
            try {
                reader.input = input
                if (reader.getWidth(0) > MAX_INPUT_SIDE || reader.getHeight(0) > MAX_INPUT_SIDE) {
                    throw InvalidInputException("사진은 한 변 ${MAX_INPUT_SIDE}px까지입니다")
                }
                return reader.read(0)
            } catch (e: InvalidInputException) {
                throw e
            } catch (e: Exception) {
                throw unreadable() // 깨진 파일·지원하지 않는 변형
            } finally {
                reader.dispose()
            }
        }
    }

    private fun unreadable() = InvalidInputException("사진을 읽을 수 없습니다. JPEG·PNG로 올려 주세요")
}
