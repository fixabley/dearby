package io.wid.dearby.batch.job

import io.wid.dearby.batch.adaptor.claude.AnthropicClientHolder
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

// 매일 한국 시간 자정에 수집한다(2026-10-15 사용자 결정). --collect-now로 띄우면 기동하자마자 한 번 더 실행한다.
// API 키가 없으면 실행하지 않고 경고만 남긴다(컨테이너가 재시작을 반복하지 않게)
@Component
class CollectionLauncher(
    private val jobOperator: JobOperator,
    private val collectActivitiesJob: Job,
    private val anthropic: AnthropicClientHolder,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *", zone = ZONE)
    fun nightly() = launch("schedule")

    override fun run(args: ApplicationArguments) {
        if (args.containsOption("collect-now")) launch("manual-${System.currentTimeMillis()}")
    }

    fun launch(trigger: String) {
        if (!anthropic.configured) {
            log.warn("ANTHROPIC_API_KEY is not set; skipping activity collection ({})", trigger)
            return
        }
        val parameters = JobParametersBuilder()
            .addString(CollectionJobConfig.RUN_DATE, LocalDate.now(ZoneId.of(ZONE)).toString())
            .addString("trigger", trigger)
            .toJobParameters()
        val execution = jobOperator.start(collectActivitiesJob, parameters)
        log.info("Activity collection {} finished: {}", trigger, execution.status)
    }

    private companion object {
        const val ZONE = "Asia/Seoul"
    }
}
