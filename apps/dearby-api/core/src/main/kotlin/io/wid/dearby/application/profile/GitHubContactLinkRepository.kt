package io.wid.dearby.application.profile

import io.wid.dearby.domain.UserId
import java.time.Duration
import java.util.*

// GitHub 연결(native-v1.md "연락처 인증")의 한 번만 꺼낼 수 있는 중간 상태. START는 인가 요청의 state, RESULT는 콜백이 받은 login을 앱이 complete로 가져가기 전까지
interface GitHubContactLinkRepository {

    fun issue(step: GitHubLinkStep, link: GitHubContactLink): UUID

    // 유효하면 지우고 돌려준다. 같은 id로 동시에 요청해도 한 번만 성공
    fun consume(id: UUID, step: GitHubLinkStep): GitHubContactLink?

    companion object {
        val TTL: Duration = Duration.ofMinutes(10)
    }
}

enum class GitHubLinkStep { START, RESULT }

// 연결을 시작한 플랫폼. 콜백이 결과를 돌려보낼 주소(앱 스킴 또는 웹)를 정한다
enum class LinkPlatform { IOS, ANDROID, WEB }

data class GitHubContactLink(val userId: UserId, val platform: LinkPlatform, val login: String? = null)
