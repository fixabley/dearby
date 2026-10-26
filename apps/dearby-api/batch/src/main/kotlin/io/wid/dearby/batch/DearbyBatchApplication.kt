package io.wid.dearby.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
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
@EnableScheduling
class DearbyBatchApplication

fun main(args: Array<String>) {
    runApplication<DearbyBatchApplication>(*args)
}
