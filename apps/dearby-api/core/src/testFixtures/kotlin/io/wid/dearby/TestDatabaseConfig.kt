package io.wid.dearby

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 테스트 DB는 실행 사이에 남으므로 컨텍스트가 뜰 때마다 비우고 처음부터 마이그레이션한다
@Configuration
class TestDatabaseConfig {

    @Bean
    fun cleanMigrateStrategy() = FlywayMigrationStrategy { flyway ->
        flyway.clean()
        flyway.migrate()
    }
}
