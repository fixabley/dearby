package io.wid.dearby.auth.security

import org.springframework.boot.context.properties.ConfigurationProperties

// 애플 로그인 설정. clientIds가 비어 있으면 애플 로그인 경로는 503(이 설정이 없어도 기동한다)
@ConfigurationProperties("dearby.apple")
data class AppleProperties(
    val clientIds: List<String> = emptyList(), // identity token의 aud로 받을 값: iOS 번들 ID, Android·웹용 Services ID
    val servicesId: String? = null, // 웹 방식(Android) 인가 요청의 client_id
    val callbackUrl: String? = null, // Services ID에 등록한 Return URL(POST /v1/auth/apple/callback)
    val appRedirectUri: String = "dearby://auth/apple", // 콜백이 결과 코드를 붙여 앱으로 돌려보내는 주소
    val jwkSetUri: String = "https://appleid.apple.com/auth/keys",
    // 토큰 받기·폐기의 client secret 서명(계정 삭제 때 애플 토큰 폐기). 셋 중 하나라도 없으면 받지도 폐기하지도 않는다
    val teamId: String? = null,
    val keyId: String? = null,
    val privateKeyPath: String? = null, // Sign in with Apple 키(.p8, PKCS#8 PEM). 저장소에 넣지 않는다
)
