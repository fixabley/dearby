package io.wid.dearby.batch.job

import io.wid.dearby.application.discovery.DiscoveryQueueService
import io.wid.dearby.application.discovery.EmbeddingService
import io.wid.dearby.application.discovery.TextEmbedder
import io.wid.dearby.application.organization.OrganizationLogoRepository
import io.wid.dearby.application.program.ProgramLogoRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 맞춤 대기열 잡(native-v1.md "맞춤 대기열"): 수집 뒤에 로고 대표색 채우기 → 활동·태그 임베딩(바뀐 것만) → 사용자별 대기열·친화도·취향.
// 모델 파일이 없으면 임베딩만 건너뛰고 대기열은 규칙·친화도로 만든다
@Configuration
class DiscoveryQueueJobConfig(
    private val embedder: TextEmbedder,
    private val embeddings: EmbeddingService,
    private val queues: DiscoveryQueueService,
    private val programLogos: ProgramLogoRepository,
    private val organizationLogos: OrganizationLogoRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun buildDiscoveryQueuesJob(jobRepository: JobRepository): Job =
        JobBuilder(JOB_NAME, jobRepository).start(
            StepBuilder("buildDiscoveryQueues", jobRepository).tasklet({ _, _ ->
                val colors = programLogos.backfillBrandColors() + organizationLogos.backfillBrandColors()
                if (colors > 0) log.info("Brand colors filled for {} logos", colors)
                val problem = embedder.problem()
                if (problem != null) {
                    log.warn("Embedding model not ready ({}); building queues without new vectors", problem)
                } else {
                    try {
                        val started = System.nanoTime()
                        val report = embeddings.embedAll(embedder)
                        log.info("Embedded {} of {} activities, {} tags with {} in {} s (failed {}, switched {})",
                            report.embedded, report.activities, report.tags, embedder.modelId, (System.nanoTime() - started) / 1_000_000_000, report.failed, report.switched)
                    } finally {
                        (embedder as? AutoCloseable)?.close() // 모델 메모리를 낮 동안 잡고 있지 않는다
                    }
                }
                log.info("Discovery queues built for {} users", queues.buildAll())
                RepeatStatus.FINISHED
            }, ResourcelessTransactionManager()).build()
        ).build()

    companion object {
        const val JOB_NAME = "buildDiscoveryQueues"
    }
}
