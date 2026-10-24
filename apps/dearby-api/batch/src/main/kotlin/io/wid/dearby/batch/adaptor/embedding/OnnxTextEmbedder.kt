package io.wid.dearby.batch.adaptor.embedding

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.wid.dearby.application.discovery.TextEmbedder
import org.springframework.stereotype.Component
import java.nio.LongBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.sqrt

// KURE·bge-m3 dense 벡터: 마지막 은닉층의 CLS(첫 토큰)를 L2 정규화한다. ONNX Runtime 기본(CPU) 실행만 쓴다(macOS CoreML 실행기는 메모리가 크게 는다).
// 모델은 처음 쓸 때 읽고 close()로 내린다(밤 배치 동안만 메모리를 쓴다)
@Component
class OnnxTextEmbedder(private val properties: EmbeddingProperties) : TextEmbedder, AutoCloseable {

    override val modelId: String get() = properties.modelId

    private var loaded: Pair<HuggingFaceTokenizer, OrtSession>? = null

    override fun problem(): String? {
        val dir = Path.of(properties.modelDir)
        return listOf(dir.resolve("tokenizer.json"), dir.resolve(properties.modelFile)).firstOrNull { !Files.isRegularFile(it) }?.let { "model file missing: $it" }
    }

    @Synchronized
    override fun embed(text: String): FloatArray {
        val (tokenizer, session) = loaded ?: load().also { loaded = it }
        val encoding = tokenizer.encode(properties.textPrefix + text)
        val ids = encoding.ids
        val shape = longArrayOf(1, ids.size.toLong())
        val env = OrtEnvironment.getEnvironment()
        val inputs = buildMap {
            put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape))
            put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(encoding.attentionMask), shape))
            if ("token_type_ids" in session.inputNames) put("token_type_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size)), shape))
        }
        try {
            session.run(inputs).use { result ->
                @Suppress("UNCHECKED_CAST")
                val hidden = (result.get(OUTPUT).orElseThrow { IllegalStateException("no $OUTPUT output") }.value as Array<Array<FloatArray>>)
                val cls = hidden[0][0]
                check(cls.size == properties.dimensions) { "expected ${properties.dimensions} dimensions, got ${cls.size}" }
                val norm = sqrt(cls.sumOf { (it * it).toDouble() }).toFloat()
                return FloatArray(cls.size) { cls[it] / norm }
            }
        } finally {
            inputs.values.forEach(OnnxTensor::close)
        }
    }

    private fun load(): Pair<HuggingFaceTokenizer, OrtSession> {
        System.setProperty("ai.djl.offline", "true") // 토크나이저 라이브러리가 네트워크로 아무것도 받지 않게
        val dir = Path.of(properties.modelDir)
        val tokenizer = HuggingFaceTokenizer.newInstance(
            dir.resolve("tokenizer.json"),
            // modelMaxLength: 내보낸 tokenizer.json은 512로 적혀 있어 그대로 두면 maxLength를 512로 줄인다
            mapOf("truncation" to "true", "maxLength" to properties.maxTokens.toString(), "modelMaxLength" to properties.maxTokens.toString(), "padding" to "false"),
        )
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(properties.threads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return tokenizer to OrtEnvironment.getEnvironment().createSession(dir.resolve(properties.modelFile).toString(), options)
    }

    @Synchronized
    override fun close() {
        loaded?.let { (tokenizer, session) -> session.close(); tokenizer.close() }
        loaded = null
    }

    private companion object {
        const val OUTPUT = "last_hidden_state"
    }
}
