package io.wid.dearby.batch.job

import io.wid.dearby.batch.application.CollectionReadiness
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import kotlin.system.exitProcess
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

// 매일 한국 시간 자정에 수집하고 이어서 맞춤 대기열을 만든다(2026-10-15·2026-10-24 사용자 결정). --collect-now로 띄우면 기동하자마자 수집을 한 번 더 실행하고,
// --build-queues-now면 대기열만 만든다.
// 수집 엔진을 쓸 수 없으면(API 키 없음, CLI 없음) 실행하지 않고 경고만 남긴다(컨테이너가 재시작을 반복하지 않게)
@Component
class CollectionLauncher(
    private val jobOperator: JobOperator,
    private val collectActivitiesJob: Job,
    private val buildDiscoveryQueuesJob: Job,
    private val readiness: CollectionReadiness,
    private val context: ConfigurableApplicationContext,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    // 수집이 끝난 뒤 맞춤 대기열을 만든다(native-v1.md "맞춤 대기열": 밤 배치, 수집 뒤). 수집 엔진이 없어도 대기열은 만든다
    @Scheduled(cron = "0 0 0 * * *", zone = ZONE)
    fun nightly() {
        launch("schedule", waitForCompletion = true)
        buildQueues("schedule")
    }

    // 일회성 실행(2026-10-19): 수동 수집이 끝나면 프로세스를 끝낸다. 스케줄과 함께 남아 정기 수집이 두 번 돌던 문제를 막는다
    override fun run(args: ApplicationArguments) {
        // --build-queues-now: 임베딩·대기열만 한 번 만들고 끝낸다
        if (args.containsOption("build-queues-now")) {
            buildQueues("manual-${System.currentTimeMillis()}")
            exitProcess(SpringApplication.exit(context))
        }
        if (!args.containsOption("collect-now")) return
        // --collect-now=12,15 이면 그 프로그램만(admin-v1.md "운영 데이터 가져오기": 새로 만든 프로그램을 바로 수집)
        val programIds = args.getOptionValues("collect-now").orEmpty().joinToString(",")
        launch("manual-${System.currentTimeMillis()}", waitForCompletion = true, programIds = programIds)
        exitProcess(SpringApplication.exit(context))
    }

    // waitForCompletion: 실행기가 비동기라 start()가 바로 돌아온다. 일회성 실행은 끝날 때까지 기다린 뒤 종료해야 수집이 실제로 돈다(2026-10-19 회귀 수정)
    fun launch(trigger: String, waitForCompletion: Boolean = false, programIds: String = "") {
        readiness.problem()?.let {
            log.warn("Collection engine not ready ({}); skipping activity collection ({})", it, trigger)
            return
        }
        val parameters = JobParametersBuilder()
            .addString(CollectionJobConfig.RUN_DATE, LocalDate.now(ZoneId.of(ZONE)).toString())
            .addString("trigger", trigger)
            .addString(CollectionJobConfig.PROGRAM_IDS, programIds)
            .toJobParameters()
        val execution = jobOperator.start(collectActivitiesJob, parameters)
        if (waitForCompletion) while (execution.isRunning) Thread.sleep(2_000)
        log.info("Activity collection {} {}: {}", trigger, if (waitForCompletion) "finished" else "launched", execution.status)
    }

    fun buildQueues(trigger: String) {
        val parameters = JobParametersBuilder().addString("trigger", trigger).toJobParameters()
        val execution = jobOperator.start(buildDiscoveryQueuesJob, parameters)
        while (execution.isRunning) Thread.sleep(2_000)
        log.info("Discovery queues {}: {}", trigger, execution.status)
    }

    private companion object {
        const val ZONE = "Asia/Seoul"
    }
}
