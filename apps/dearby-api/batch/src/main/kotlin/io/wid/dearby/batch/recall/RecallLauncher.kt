package io.wid.dearby.batch.recall

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// 매일 한국 시간 04:00에 회수한다(2026-10-19 사용자 결정). --recall-now로 띄우면 기동하자마자 한 번 더 실행한다(--collect-now와 같은 방식)
@Component
class RecallLauncher(private val recall: RecallService) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun daily() = launch("schedule")

    override fun run(args: ApplicationArguments) {
        if (args.containsOption("recall-now")) launch("manual-${System.currentTimeMillis()}")
    }

    fun launch(trigger: String) {
        val report = recall.recall()
        log.info("Card recall {} finished: {} receipts, {} guest cards recalled", trigger, report.receipts, report.guestCards)
    }
}
