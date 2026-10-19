package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.batch.application.CollectionReadiness
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.util.concurrent.TimeUnit

// Claude Code CLI를 비대화형(-p)으로 한 번 실행한다. 구독 로그인(로컬) 또는 CLAUDE_CODE_OAUTH_TOKEN(컨테이너)으로 인증한다.
// 기본 시스템 프롬프트·사용자 설정·이번 실행 밖의 MCP·세션 저장을 쓰지 않고, 빈 임시 폴더에서 돌아 CLAUDE.md를 읽지 않는다.
// 자식 프로세스에는 실행에 필요한 환경변수만 넘긴다(DB 비밀번호 등은 넘기지 않음)
@Component
open class ClaudeCli(
    private val properties: CollectionProperties,
    private val json: JsonMapper,
) : CollectionReadiness {

    override fun problem(): String? {
        // macOS는 키체인의 구독 로그인을 쓴다. 컨테이너(Linux)는 setup-token으로 만든 토큰이 있어야 한다
        if (!System.getProperty("os.name").startsWith("Mac") && System.getenv("CLAUDE_CODE_OAUTH_TOKEN").isNullOrBlank()) {
            return "CLAUDE_CODE_OAUTH_TOKEN is not set"
        }
        return cliProblem()
    }

    private fun cliProblem(): String? = try {
        val process = ProcessBuilder(properties.cliPath, "--version").redirectErrorStream(true).start()
        if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) null else "claude CLI --version failed"
    } catch (e: Exception) {
        "claude CLI not available: ${e.message}"
    }

    // 수집 에이전트 한 번: 내장 도구는 WebSearch만, 페이지 받기·제출은 이번 실행의 도구 서버(mcpUrl)로만 한다.
    // prompt는 표준 입력으로 넘긴다(명령 인자 길이 제한). CLI 결과 JSON(num_turns·usage·total_cost_usd·is_error·subtype)을 그대로 돌려준다
    open fun run(systemPrompt: String, prompt: String, mcpUrl: String): JsonNode {
        val workDir = Files.createTempDirectory("dearby-claude-")
        try {
            // 출력은 파일로 받는다. 파이프를 읽으며 기다리면 CLI가 멈췄을 때 시간 제한이 걸리지 않는다
            val stdoutFile = workDir.resolve("stdout.json").toFile()
            val stderrFile = workDir.resolve("stderr.txt").toFile()
            val builder = ProcessBuilder(command(systemPrompt, mcpUrl)).directory(workDir.toFile()).redirectOutput(stdoutFile).redirectError(stderrFile)
            builder.environment().keys.retainAll(PASSED_ENV)
            // 도구 결과(페이지 본문 MAX_PAGE_CHARS자)가 Claude Code의 MCP 결과 한도(기본 25,000토큰)에 잘리지 않게 한다
            builder.environment()["MAX_MCP_OUTPUT_TOKENS"] = MAX_MCP_OUTPUT_TOKENS
            val process = builder.start()
            process.outputStream.bufferedWriter().use { it.write(prompt) }
            if (!process.waitFor(properties.cliTimeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IllegalStateException("claude CLI timed out after ${properties.cliTimeout}")
            }
            return runCatching { json.readTree(stdoutFile.readText()) }.getOrNull()
                ?: throw IllegalStateException("claude CLI exit ${process.exitValue()}: ${stderrFile.readText().take(500)}")
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    fun command(systemPrompt: String, mcpUrl: String): List<String> = listOf(
        properties.cliPath, "-p",
        "--model", properties.model,
        "--output-format", "json",
        "--system-prompt", systemPrompt,
        "--max-turns", properties.maxTurns.toString(),
        "--mcp-config", json.writeValueAsString(mapOf("mcpServers" to mapOf(SERVER to mapOf("type" to "http", "url" to mcpUrl)))),
        "--strict-mcp-config",
        "--tools", BUILT_IN_TOOLS.joinToString(","),
        "--allowedTools", (BUILT_IN_TOOLS + AGENT_TOOLS).joinToString(","),
        "--setting-sources", "",
        "--no-session-persistence",
    )

    companion object {
        const val SERVER = "dearby"
        private const val MAX_MCP_OUTPUT_TOKENS = "40000"
        // 웹 검색만 내장 도구로 쓴다(시작 주소가 없는 프로그램의 후보 찾기). WebFetch·Bash 등은 없다
        val BUILT_IN_TOOLS = listOf("WebSearch")
        val AGENT_TOOLS = listOf(AgentToolbox.FETCH, AgentToolbox.RENDER, AgentToolbox.SUBMIT, AgentToolbox.FINISH).map { "mcp__${SERVER}__$it" }
        private val PASSED_ENV = setOf("HOME", "PATH", "LANG", "TMPDIR", "USER", "CLAUDE_CODE_OAUTH_TOKEN")
    }
}
