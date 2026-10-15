package io.wid.dearby.batch.adaptor.claude

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

// engine: api(Anthropic API, ANTHROPIC_API_KEY) 또는 cli(Claude Code CLI, 구독 로그인·CLAUDE_CODE_OAUTH_TOKEN)
@ConfigurationProperties("dearby.collection")
data class CollectionProperties(
    val engine: String = ENGINE_API,
    val model: String = "claude-haiku-4-5",
    val cliPath: String = "claude",
    val cliTimeout: Duration = Duration.ofMinutes(4),
) {
    companion object {
        const val ENGINE_API = "api"
        const val ENGINE_CLI = "cli"
    }
}
