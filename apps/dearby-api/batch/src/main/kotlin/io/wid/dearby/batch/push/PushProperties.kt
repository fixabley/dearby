package io.wid.dearby.batch.push

import org.springframework.boot.context.properties.ConfigurationProperties

// 푸시 인증 정보(native-v1.md "발송 경로와 인증 정보"). 환경값으로만 받고(운영 .env.prod) 로그에 남기지 않는다. 비어 있으면 그 플랫폼 발송만 건너뛴다
@ConfigurationProperties("dearby.push")
data class PushProperties(
    val apnsKeyP8: String = "", // .p8 내용(PEM) 또는 그 파일 경로
    val apnsKeyId: String = "",
    val apnsTeamId: String = "",
    val apnsTopic: String = "io.wid.dearby", // 앱 번들 id
    val apnsEnv: String = "production", // production 또는 development(샌드박스, 개발 빌드 토큰)
    val fcmProjectId: String = "",
    val fcmCredentialsPath: String = "", // Firebase 서비스 계정 JSON 파일 경로(서버 발송 자격)
    val webBaseUrl: String = "https://dearby.wid.io.kr", // 활동 링크(유니버설 링크·App Links) 기준
)
