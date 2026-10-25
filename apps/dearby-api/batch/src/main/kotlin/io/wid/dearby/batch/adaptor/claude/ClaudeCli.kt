package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.batch.application.CollectionReadiness
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.HexFormat
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

    // 한 번의 실행 결과. workflowVersion은 넘긴 작업 절차 파일의 SHA-256 앞 12자리
    data class Run(val result: JsonNode, val workflowVersion: String)

    // 수집 에이전트 한 번: 작업 절차는 WORKFLOW.md(--system-prompt-file), 내장 도구는 WebSearch만, 페이지 받기·제출은 이번 실행의 도구 서버(mcpUrl)로만 한다.
    // CLI 결과 JSON(num_turns·usage·total_cost_usd·is_error·subtype)을 그대로 돌려준다
    fun run(prompt: String, mcpUrl: String): Run {
        val workDir = Files.createTempDirectory("dearby-claude-")
        try {
            val workflow = workflow(workDir)
            val version = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(workflow))).take(12)
            return Run(execute(command(workflow, mcpConfig(workDir, mcpUrl)), prompt, workDir), version)
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    // 도구 없이 한 번 묻는다(게시 활동 대기열 글 채우기, 2026-10-25). 페이지를 받지 않고 넘긴 글만 본다. 모델 답 글(result)을 돌려준다
    fun ask(systemPrompt: String, prompt: String): String {
        val workDir = Files.createTempDirectory("dearby-claude-")
        try {
            val command = listOf(
                properties.cliPath, "-p", "--model", properties.model, "--output-format", "json", "--system-prompt", systemPrompt,
                "--max-turns", "1", "--tools", "", "--strict-mcp-config", "--setting-sources", "", "--no-session-persistence",
            )
            val result = execute(command, prompt, workDir)
            if (result.path("is_error").asBoolean(false)) throw IllegalStateException("claude CLI failed: ${result.path("subtype").asString("")}")
            return result.path("result").asString("")
        } finally {
            workDir.toFile().deleteRecursively()
        }
    }

    // 운영에서 내려 둔 파일(COLLECTION_WORKFLOW_PATH)이 있으면 그것, 없으면 jar 안의 기본 파일을 이번 실행 폴더에 꺼내 쓴다.
    // 마운트할 파일이 없으면 Docker가 그 자리에 폴더를 만들므로 일반 파일인지 본다
    fun workflow(workDir: Path): Path =
        properties.workflowPath?.takeIf { it.isNotBlank() }?.let { Path.of(it) }?.takeIf { Files.isRegularFile(it) }
            ?: workDir.resolve("WORKFLOW.md").also { target ->
                javaClass.getResourceAsStream(WORKFLOW_RESOURCE)!!.use { Files.copy(it, target) }
            }

    // prompt는 표준 입력으로 넘긴다(명령 인자 길이 제한). 출력은 파일로 받는다. 파이프를 읽으며 기다리면 CLI가 멈췄을 때 시간 제한이 걸리지 않는다
    protected open fun execute(command: List<String>, prompt: String, workDir: Path): JsonNode {
        val stdoutFile = workDir.resolve("stdout.json").toFile()
        val stderrFile = workDir.resolve("stderr.txt").toFile()
        val builder = ProcessBuilder(command).directory(workDir.toFile()).redirectOutput(stdoutFile).redirectError(stderrFile)
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
    }

    // 도구 서버 주소(비밀 경로 포함)는 명령 인자에 두지 않고(ps에 보인다) 이번 실행 폴더의 소유자만 읽는 파일로 넘긴다. 폴더째 실행 뒤 지운다
    private fun mcpConfig(workDir: Path, mcpUrl: String): Path {
        val file = Files.createFile(workDir.resolve("mcp.json"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        Files.writeString(file, json.writeValueAsString(mapOf("mcpServers" to mapOf(SERVER to mapOf("type" to "http", "url" to mcpUrl)))))
        return file
    }

    fun command(workflow: Path, mcpConfig: Path): List<String> = listOf(
        properties.cliPath, "-p",
        "--model", properties.model,
        "--output-format", "json",
        "--system-prompt-file", workflow.toString(),
        "--max-turns", properties.maxTurns.toString(),
        "--max-budget-usd", properties.maxBudgetUsd.toPlainString(),
        "--mcp-config", mcpConfig.toString(),
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
        val AGENT_TOOLS = listOf(AgentToolbox.FETCH, AgentToolbox.RENDER, AgentToolbox.SUBMIT, AgentToolbox.SEARCH_FEEDBACK, AgentToolbox.FINISH).map { "mcp__${SERVER}__$it" }
        const val WORKFLOW_RESOURCE = "/collector/WORKFLOW.md"
        private val PASSED_ENV = setOf("HOME", "PATH", "LANG", "TMPDIR", "USER", "CLAUDE_CODE_OAUTH_TOKEN")
    }
}
