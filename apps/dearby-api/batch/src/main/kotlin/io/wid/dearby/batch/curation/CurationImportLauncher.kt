package io.wid.dearby.batch.curation

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component
import java.nio.file.Path
import kotlin.system.exitProcess

// 일회성 실행: --import-curation=<파일> [--apply]. 기본은 계획만 보여 주는 dry run이고, 끝나면 프로세스를 끝낸다(--collect-now와 같은 방식)
@Component
class CurationImportLauncher(private val service: CurationImportService, private val context: ConfigurableApplicationContext) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val file = args.getOptionValues("import-curation")?.singleOrNull() ?: return
        val apply = args.containsOption("apply")
        val lines = service.import(Path.of(file), apply)
        lines.forEach { log.info("{} {} {} {}", it.action, it.target, it.field, it.detail) }
        log.info(
            "Curation import {} finished: {} set, {} skipped",
            if (apply) "apply" else "dry run", lines.count { it.action == "SET" }, lines.count { it.action == "SKIP" },
        )
        summary(lines).forEach { log.info("{}", it) }
        // 새로 만든 프로그램 id(바로 수집하려면 --collect-now=<id,…>)
        lines.filter { it.field == "created" && it.target.startsWith("program ") }
            .mapNotNull { Regex("→ id (\\d+)").find(it.detail)?.groupValues?.get(1) }
            .takeIf { it.isNotEmpty() }?.let { log.info("New program ids: {}", it.joinToString(",")) }
        exitProcess(SpringApplication.exit(context))
    }

    // 활동별 일정 요약: 만들 블록·지울 블록·고칠 블록·붙일 시간표 세션 수·건너뜀(검증 오류 포함)
    private fun summary(lines: List<CurationImportService.Line>) = lines.filter { it.target.startsWith("activity ") }
        .groupBy { it.target.split(" ").take(2).joinToString(" ") }
        .map { (activity, rows) ->
            fun set(field: String) = rows.count { it.field == field && it.action == "SET" }
            val sessions = rows.filter { it.field == "timetable" && it.action == "SET" }.sumOf { Regex("세션 (\\d+)개").find(it.detail)?.groupValues?.get(1)?.toInt() ?: 0 }
            "$activity: 블록 만들기 ${set("schedule-create")}, 지우기 ${set("schedule-delete")}, 고치기 ${set("schedule-edit")}, 시간표 세션 $sessions, 건너뜀 ${rows.count { it.action == "SKIP" }}" +
                rows.filter { it.field == "-" }.joinToString("") { " | 오류: ${it.detail}" }
        }
}
