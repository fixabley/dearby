package io.wid.dearby.batch.adaptor.claude

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

// 수집 에이전트(Claude Code CLI, 구독 로그인·CLAUDE_CODE_OAUTH_TOKEN). cliTimeout은 프로그램 하나의 루프 전체,
// maxTurns는 CLI --max-turns, maxPages는 도구 서버가 받아 주는 페이지 수(프로그램당)
@ConfigurationProperties("dearby.collection")
data class CollectionProperties(
    val model: String = "claude-haiku-4-5",
    val cliPath: String = "claude",
    val cliTimeout: Duration = Duration.ofMinutes(15),
    val maxTurns: Int = 12,
    val maxPages: Int = 15,
)
