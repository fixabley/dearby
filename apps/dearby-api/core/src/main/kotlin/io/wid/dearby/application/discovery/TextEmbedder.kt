package io.wid.dearby.application.discovery

// 글 → 벡터(native-v1.md "활동 벡터"). 서버 안에서만 돈다(외부 API 없음). modelId는 벡터와 함께 저장해 같은 모델끼리만 비교한다
interface TextEmbedder {
    val modelId: String

    // 쓸 수 없으면(모델 파일 없음 등) 그 이유, 쓸 수 있으면 null
    fun problem(): String?

    // 단위 길이 벡터(차원은 저장 열 halfvec(1024)과 같아야 한다)
    fun embed(text: String): FloatArray
}
