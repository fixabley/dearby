package io.wid.dearby.batch

import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

// core의 저장소(adaptor.persistence)와 활동·프로그램 조회만 쓴다. 인증 등 api 전용 빈은 올리지 않는다
// 맞춤 대기열(native-v1.md "맞춤 대기열")은 활동·프로그램 묶음·추천 규칙·대기열 서비스를 함께 쓴다
@SpringBootApplication(
    scanBasePackages = [
        "io.wid.dearby.batch", "io.wid.dearby.adaptor.persistence", "io.wid.dearby.application.catalog", "io.wid.dearby.application.activity",
        "io.wid.dearby.application.program", "io.wid.dearby.application.recommendation", "io.wid.dearby.application.discovery",
        "io.wid.dearby.application.timetable", // 운영 데이터 가져오기의 시간표(admin-v1.md "트랙·세션 시간표")
    ]
)
@ConfigurationPropertiesScan
class DearbyBatchApplication

// 정기 작업(자정 수집·매시 알림·04:00 회수). 일회성 실행(oneOffRun)에서는 꺼서 그 프로세스가 끝나기 전에 정기 작업이 함께 돌지 않게 한다(2026-10-26)
@Configuration
@EnableScheduling
@ConditionalOnProperty("dearby.scheduling.enabled", matchIfMissing = true)
class SchedulingConfig

// 각 Launcher가 받아 한 번 실행하고 프로세스를 끝내는 명령행 옵션
val ONE_OFF_OPTIONS = setOf("collect-now", "build-queues-now", "send-push-now", "backfill-hooks-now", "recall-now", "import-curation")

fun oneOffRun(args: Array<String>) = DefaultApplicationArguments(*args).optionNames.any { it in ONE_OFF_OPTIONS }

fun main(args: Array<String>) {
    runApplication<DearbyBatchApplication>(*args) {
        // 기본값이라 명령행의 --dearby.scheduling.enabled=true가 이긴다
        if (oneOffRun(args)) setDefaultProperties(mapOf("dearby.scheduling.enabled" to "false"))
    }
}
