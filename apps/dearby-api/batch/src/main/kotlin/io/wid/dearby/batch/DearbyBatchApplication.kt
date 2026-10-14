package io.wid.dearby.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// core의 저장소(adaptor.persistence)와 활동·프로그램 조회만 쓴다. 인증 등 api 전용 빈은 올리지 않는다
@SpringBootApplication(scanBasePackages = ["io.wid.dearby.batch", "io.wid.dearby.adaptor.persistence"])
@ConfigurationPropertiesScan
@EnableScheduling
class DearbyBatchApplication

fun main(args: Array<String>) {
    runApplication<DearbyBatchApplication>(*args)
}
