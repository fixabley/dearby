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
        exitProcess(SpringApplication.exit(context))
    }
}
