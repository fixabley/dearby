package io.wid.dearby.batch.recall

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import kotlin.system.exitProcess
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// 매일 한국 시간 04:00에 회수하고 보유 기간이 지난 명함 신고·비로그인 차단을 지운다(2026-10-19 사용자 결정). --recall-now로 띄우면 기동하자마자 한 번 더 실행한다(--collect-now와 같은 방식)
@Component
class RecallLauncher(private val recall: RecallService, private val context: ConfigurableApplicationContext) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun daily() = launch("schedule")

    // 일회성 실행(2026-10-19): 수동 회수가 끝나면 프로세스를 끝낸다(--collect-now와 같다)
    override fun run(args: ApplicationArguments) {
        if (!args.containsOption("recall-now")) return
        launch("manual-${System.currentTimeMillis()}")
        exitProcess(SpringApplication.exit(context))
    }

    fun launch(trigger: String) {
        val report = recall.recall()
        log.info("Card recall {} finished: {} receipts, {} guest cards recalled, {} old reports and {} guest blocks removed", trigger, report.receipts, report.guestCards, report.reports, report.guestBlocks)
    }
}
