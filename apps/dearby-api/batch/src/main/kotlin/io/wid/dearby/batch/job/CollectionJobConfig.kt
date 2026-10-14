package io.wid.dearby.batch.job

import io.wid.dearby.application.collection.CollectionRunRepository
import io.wid.dearby.application.collection.CollectionTargetRepository
import io.wid.dearby.batch.application.CollectActivitiesService
import io.wid.dearby.domain.CollectionOutcome
import io.wid.dearby.domain.CollectionReport
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate

// 활동 수집 잡. 수집이 켜져 있고 주기가 지난 프로그램마다 한 번씩 수집하고 결과를 collection_runs에 남긴다.
// 한 프로그램이 실패해도 다음 프로그램을 계속한다. 같은 프로그램·날짜는 collection_runs가 한 번만 허용한다
@Configuration
class CollectionJobConfig(
    private val targets: CollectionTargetRepository,
    private val runs: CollectionRunRepository,
    private val collector: CollectActivitiesService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun collectActivitiesJob(jobRepository: JobRepository): Job =
        JobBuilder(JOB_NAME, jobRepository).start(collectStep(jobRepository)).build()

    // 모델·페이지 호출이 길어 DB 트랜잭션으로 감싸지 않는다. 저장은 저장소가 각자 트랜잭션으로 한다
    private fun collectStep(jobRepository: JobRepository): Step =
        StepBuilder("collectActivities", jobRepository).tasklet({ _, chunkContext ->
            val runDate = LocalDate.parse(chunkContext.stepContext.jobParameters[RUN_DATE] as String)
            for (target in targets.findDue(runDate)) {
                val runId = runs.start(target.programId, runDate, target.officialOnly) ?: continue
                val report = try {
                    collector.collect(target)
                } catch (e: Exception) {
                    log.warn("Collection failed for program {}", target.programId, e)
                    CollectionReport(CollectionOutcome.FAILED, error = e.message ?: e.javaClass.simpleName)
                }
                runs.finish(runId, report)
                log.info(
                    "Collected program {}: {} found {} saved {}",
                    target.programId,
                    report.outcome,
                    report.foundCount,
                    report.savedCount
                )
            }
            RepeatStatus.FINISHED
        }, ResourcelessTransactionManager()).build()

    companion object {
        const val JOB_NAME = "collectActivities"
        const val RUN_DATE = "runDate"
    }
}
