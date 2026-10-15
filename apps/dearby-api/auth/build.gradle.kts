plugins {
    `java-library`
}

// 인증 공통 모듈: 패스키 가입·로그인, access·refresh token 발급과 검증, 보안 기본 설정, 오류 응답.
// 실행 앱(api·admin)은 io.wid.dearby.auth를 스캔하고 AccessRules 빈으로 자기 경로 권한만 정한다
dependencies {
    api(project(":core"))
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    api("org.springframework.security:spring-security-webauthn")
}
