package io.wid.dearby

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DearbyApiApplication

fun main(args: Array<String>) {
    runApplication<DearbyApiApplication>(*args)
}
