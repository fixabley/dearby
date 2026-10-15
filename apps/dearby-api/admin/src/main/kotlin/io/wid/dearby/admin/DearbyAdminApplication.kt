package io.wid.dearby.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

// core(저장소·application)와 공통 인증(auth: 패스키 로그인·토큰)을 쓴다. 공개 API(api의 웹 어댑터)는 올리지 않는다
@SpringBootApplication(
    scanBasePackages = ["io.wid.dearby.admin", "io.wid.dearby.auth", "io.wid.dearby.adaptor.persistence", "io.wid.dearby.application"]
)
@ConfigurationPropertiesScan
class DearbyAdminApplication

fun main(args: Array<String>) {
    runApplication<DearbyAdminApplication>(*args)
}
