package io.wid.dearby.application.profile

import java.net.URI
import java.util.*

// GitHub OAuth 쪽 포트(native-v1.md "연락처 인증"). 구현(OAuth App 설정·HTTP)은 api 모듈 adaptor/github. 어드민에는 구현이 없어 서비스가 null로 받는다
interface GitHubAccounts {

    // GITHUB_CLIENT_ID·GITHUB_CLIENT_SECRET·콜백 주소가 모두 있으면 true
    fun isConfigured(): Boolean

    // 사용자 브라우저가 열 GitHub 인가 주소. scope 없음(공개 프로필의 login만)
    fun authorizeUrl(state: UUID): String

    // 인가 code를 액세스 토큰으로 바꿔 GET /user의 login을 읽는다. 토큰은 저장하지 않는다. 실패하면 null
    fun login(code: String): String?

    // 콜백이 결과(code 또는 error)를 붙여 앱·웹으로 돌려보낼 주소
    fun redirect(platform: LinkPlatform, name: String, value: String): URI
}
