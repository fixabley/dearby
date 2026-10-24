package io.wid.dearby.application.discovery

import io.wid.dearby.domain.Id

// 활동·태그 벡터 쓰기(배치만). 구현은 adaptor/persistence
interface EmbeddingRepository {
    fun activityHashes(model: String): Map<Id, String>
    fun saveActivity(activityId: Id, model: String, vector: FloatArray, hash: String)
    fun tagHashes(model: String): Map<Id, String>
    fun saveTag(tagId: Id, model: String, vector: FloatArray, hash: String)

    // 읽는 모델을 바꾸고 다른 모델의 활동·태그 벡터와 취향을 지운다(새 모델 벡터를 다 만든 뒤에만 부른다)
    fun switchModel(model: String)
}
