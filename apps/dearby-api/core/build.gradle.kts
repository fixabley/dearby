plugins {
    `java-library`
    `java-test-fixtures`
    id("org.flywaydb.flyway")
    id("org.jooq.jooq-codegen-gradle")
}

val flywayMigration: Configuration = configurations.create("flywayMigration")

dependencies {
    api("org.springframework.boot:spring-boot-starter-flyway")
    api("org.springframework.boot:spring-boot-starter-jooq")
    api("org.flywaydb:flyway-database-postgresql")
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework:spring-web") // 장소 검색 제공자(RestClient, native-v1.md "수집기 장소"). 어드민·batch가 함께 쓴다
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0") // 로고 대표색 계산에 WebP 읽기(ImageIO에는 WebP가 없다)
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // 실행 앱(api 등)의 테스트가 함께 쓰는 테스트 DB 설정(TestDatabaseConfig, application.properties)
    testFixturesApi("org.springframework.boot:spring-boot-starter-flyway")
    flywayMigration("org.postgresql:postgresql")
    jooqCodegen("org.postgresql:postgresql")
}

// 테스트·코드 생성용 PostgreSQL: compose.yaml의 postgres-test(127.0.0.1:5433). CI는 같은 값의 서비스 컨테이너를 띄운다
val testDbUrl = "jdbc:postgresql://localhost:${System.getenv("TEST_DB_PORT") ?: "5433"}/dearby_test"
val testDbUser = "dearby_test"

// jOOQ 코드 생성: 테스트 DB를 비우고 마이그레이션을 적용한 뒤 그 스키마로 생성한다. 결과(core/src/generated/jooq)는 커밋한다.
// 그래서 컴파일·운영 이미지 빌드에는 DB가 필요 없다. 마이그레이션을 바꾸면 ./gradlew :core:jooqCodegen 후 함께 커밋한다(CI가 확인)
val migrations = layout.projectDirectory.dir("src/main/resources/db/migration")
val jooqOutput = layout.projectDirectory.dir("src/generated/jooq")

flyway {
    url = testDbUrl
    user = testDbUser
    password = testDbUser
    locations = arrayOf("filesystem:${migrations.asFile.absolutePath}")
    configurations = arrayOf(flywayMigration.name)
    cleanDisabled = false
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = testDbUrl
            user = testDbUser
            password = testDbUser
        }
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                includes = ".*"
                excludes = "flyway_schema_history|batch_.*" // Spring Batch 테이블은 Spring Batch가 직접 쓴다
                includeRoutines = false // 확장(pg_trgm 등) 함수는 쓰지 않는다
            }
            target {
                packageName = "io.wid.dearby.adaptor.persistence.jooq"
                directory = jooqOutput.asFile.absolutePath
            }
        }
    }
}

tasks.flywayMigrate {
    dependsOn(tasks.flywayClean) // 매번 빈 스키마에서 전체 마이그레이션
}

tasks.jooqCodegen {
    dependsOn(tasks.flywayMigrate)
}

sourceSets.main {
    kotlin.srcDir(jooqOutput)
}
