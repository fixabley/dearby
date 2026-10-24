package io.wid.dearby.batch.adaptor.embedding

import org.springframework.boot.context.properties.ConfigurationProperties

// 활동 벡터 모델(native-v1.md "활동 벡터"). 벡터는 modelId와 함께 저장되고 같은 modelId끼리만 비교하므로, 모델·가중치 판을 바꾸면 modelId도 바꾼다.
// 기본은 nlpai-lab/KURE-v1(bge-m3 구조, 1024차원, CLS 풀링)을 batch/Dockerfile이 고정 판·체크섬으로 ONNX로 바꾼 int8 판(ONNX Runtime에서 fp32는 느리다).
// 384차원 소형(multilingual-e5-small 등)으로 바꾸려면 dimensions=384, textPrefix="passage: "와 함께 halfvec 열 차원을 바꾸는 마이그레이션이 필요하다
@ConfigurationProperties("dearby.embedding")
data class EmbeddingProperties(
    val modelId: String = "nlpai-lab/KURE-v1@8b418a5:int8",
    val modelDir: String = "/app/models/kure-v1", // tokenizer.json과 modelFile이 있는 폴더. 없으면 임베딩만 건너뛴다
    val modelFile: String = "model.onnx",
    val dimensions: Int = 1024,
    val textPrefix: String = "", // e5 계열은 "passage: "(모든 글에 같은 접두어). bge-m3·KURE는 없음
    val maxTokens: Int = 8192, // 모델 최대 입력(KURE·bge-m3 8192). 넘는 글은 뒤를 자른다(설명이 맨 뒤라 설명 끝이 잘린다)
    val threads: Int = 4,
)
