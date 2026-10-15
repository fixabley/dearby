package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.batch.application.CollectionReadiness
import io.wid.dearby.batch.application.ModelUsage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.util.concurrent.TimeUnit

// Claude Code CLI를 비대화형(-p)으로 한 번 실행한다. 구독 로그인(로컬) 또는 CLAUDE_CODE_OAUTH_TOKEN(컨테이너)으로 인증한다.
// 기본 시스템 프롬프트·사용자 설정·MCP·세션 저장을 쓰지 않아 요청이 작고, 빈 임시 폴더에서 돌아 CLAUDE.md를 읽지 않는다.
// 자식 프로세스에는 실행에 필요한 환경변수만 넘긴다(DB 비밀번호 등은 넘기지 않음)
@Component
@ConditionalOnProperty("dearby.collection.engine", havingValue = CollectionProperties.ENGINE_CLI)
class ClaudeCli(
    private val properties: CollectionProperties,
    private val json: JsonMapper,
) : CollectionReadiness {

    data class Result(val output: JsonNode, val usage: ModelUsage)

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

    // prompt는 표준 입력으로 넘긴다(본문이 길어 명령 인자 길이 제한을 넘을 수 있다)
    fun run(systemPrompt: String, prompt: String, schema: Map<String, Any>, tools: List<String>): Result {
        val workDir = Files.createTempDirectory("dearby-claude-")
        try {
            val command = listOf(
                properties.cliPath, "-p",
                "--model", properties.model,
                "--output-format", "json",
                "--system-prompt", systemPrompt,
                "--json-schema", json.writeValueAsString(schema),
                "--tools", tools.joinToString(","),
                "--setting-sources", "",
                "--strict-mcp-config",
                "--no-session-persistence",
            ) + if (tools.isEmpty()) emptyList() else listOf("--allowedTools", tools.joinToString(","))
            // 출력은 파일로 받는다. 파이프를 읽으며 기다리면 CLI가 멈췄을 때 시간 제한이 걸리지 않는다
            val stdoutFile = workDir.resolve("stdout.json").toFile()
            val stderrFile = workDir.resolve("stderr.txt").toFile()
            val builder = ProcessBuilder(command).directory(workDir.toFile()).redirectOutput(stdoutFile).redirectError(stderrFile)
            builder.environment().keys.retainAll(PASSED_ENV)
            val process = builder.start()
            process.outputStream.bufferedWriter().use { it.write(prompt) }
            if (!process.waitFor(properties.cliTimeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IllegalStateException("claude CLI timed out after ${properties.cliTimeout}")
            }
            val stdout = stdoutFile.readText()
            val stderr = stderrFile.readText()
            val result = runCatching { json.readTree(stdout) }.getOrNull()
                ?: throw IllegalStateException("claude CLI exit ${process.exitValue()}: ${stderr.take(500)}")
            if (result.path("is_error").asBoolean(false) || result.path("structured_output").isMissingNode) {
                throw IllegalStateException("claude CLI failed: ${result.path("subtype").asString("")} ${result.path("result").asString("").take(500)}")
            }
            val usage = result.path("usage")
            return Result(
                output = result.path("structured_output"),
                usage = ModelUsage(
                    inputTokens = usage.path("input_tokens").asLong(0) + usage.path("cache_creation_input_tokens").asLong(0) +
                        usage.path("cache_read_input_tokens").asLong(0),
                    outputTokens = usage.path("output_tokens").asLong(0),
                    webSearchRequests = usage.path("server_tool_use").path("web_search_requests").asInt(0),
                ),
            )
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    private companion object {
        val PASSED_ENV = setOf("HOME", "PATH", "LANG", "TMPDIR", "USER", "CLAUDE_CODE_OAUTH_TOKEN")
    }
}
