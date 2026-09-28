package com.dearby.nativeapp.app.providers

import java.time.Instant
import java.time.ZoneId

fun receivedDate(utc: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { Instant.parse(utc).atZone(zone).toLocalDate().toString() }.getOrDefault("날짜 확인 필요")
