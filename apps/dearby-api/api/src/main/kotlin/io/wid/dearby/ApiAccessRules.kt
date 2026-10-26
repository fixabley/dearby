package io.wid.dearby

import io.wid.dearby.auth.security.AccessRules
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.AuthorizeHttpRequestsDsl
import org.springframework.stereotype.Component

// 공개 API: 활동·프로그램 묶음 조회는 로그인 없이, 나머지는 로그인 필요
@Component
class ApiAccessRules : AccessRules {

    override fun apply(rules: AuthorizeHttpRequestsDsl) = with(rules) {
        authorize(HttpMethod.GET, "/v1/activities", permitAll)
        authorize(HttpMethod.GET, "/v1/activities/*", permitAll)
        // 발견 프로그램 묶음(native-v1.md "발견 프로그램 묶음")
        authorize(HttpMethod.GET, "/v1/discovery/programs", permitAll)
        // 프로그램 로고 이미지(native-v1.md "프로그램 로고")
        authorize(HttpMethod.GET, "/v1/programs/*/logo", permitAll)
        // 프로그램 커버 이미지(native-v1.md "맞춤 대기열")
        authorize(HttpMethod.GET, "/v1/programs/*/cover", permitAll)
        // 조직 로고 이미지(native-v1.md "조직 로고와 로고 계승")
        authorize(HttpMethod.GET, "/v1/organizations/*/logo", permitAll)
        // 태그 사전(native-v1.md "태그")
        authorize(HttpMethod.GET, "/v1/tags", permitAll)
        // 공개 명함(native-v1.md "프로필·명함 v2")
        authorize(HttpMethod.GET, "/v1/cards/*", permitAll)
        // 명함 사진: 볼 근거는 명함 조회와 같다(CardService.photo, native-v1.md "프로필 사진")
        authorize(HttpMethod.GET, "/v1/cards/*/photo", permitAll)
        // 명함 신고: 게스트·QR 코드로 본 사람도 한다. 볼 권한은 CardReportService가 확인한다(native-v1.md "명함 신고·차단")
        authorize(HttpMethod.POST, "/v1/cards/*/reports", permitAll)
        // 웹 비로그인 저장: 로그인 대신 프록시 키·세션 토큰 헤더를 본다(adaptor/web/v1/guest)
        authorize("/v1/guest/**", permitAll)
        authorize(anyRequest, authenticated)
    }
}
