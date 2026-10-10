plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.flywaydb.flyway") version "12.4.0"
    id("org.jooq.jooq-codegen-gradle") version "3.21.7"
}

group = "io.wid"
version = "0.0.1-SNAPSHOT"
description = "dearby-api"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["springModulithVersion"] = "2.1.1"

val flywayMigration: Configuration = configurations.create("flywayMigration")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.security:spring-security-webauthn")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
    runtimeOnly("org.xerial:sqlite-jdbc")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    flywayMigration("com.h2database:h2")
    jooqCodegen("com.h2database:h2")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 로컬 실행은 local 프로필(application-local.yaml). SPRING_PROFILES_ACTIVE로 바꿀 수 있다
tasks.bootRun {
    environment("SPRING_PROFILES_ACTIVE", System.getenv("SPRING_PROFILES_ACTIVE") ?: "local")
}

// jOOQ 코드 생성: 마이그레이션을 H2(PostgreSQL 모드)에 적용한 뒤 그 스키마로 생성
// https://www.jooq.org/doc/latest/manual/getting-started/tutorials/jooq-with-flyway/
val migrations = layout.projectDirectory.dir("src/main/resources/db/migration")
val codegenDbDir = layout.buildDirectory.dir("jooq-codegen-db")
val codegenDbUrl = codegenDbDir.map {
    "jdbc:h2:file:${it.asFile.absolutePath}/db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=VALUE"
}
val jooqOutput = layout.buildDirectory.dir("generated-src/jooq/main")

flyway {
    url = codegenDbUrl.get()
    user = "sa"
    locations = arrayOf("filesystem:${migrations.asFile.absolutePath}")
    configurations = arrayOf(flywayMigration.name)
}

jooq {
    configuration {
        jdbc {
            driver = "org.h2.Driver"
            url = codegenDbUrl.get()
            user = "sa"
        }
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                name = "org.jooq.meta.h2.H2Database"
                inputSchema = "public"
                includes = ".*"
                excludes = "flyway_schema_history"
            }
            target {
                packageName = "io.wid.dearby.adaptor.persistence.jooq"
                directory = jooqOutput.get().asFile.absolutePath
            }
        }
    }
}

tasks.flywayMigrate {
    inputs.dir(migrations)
    outputs.dir(codegenDbDir)
    doFirst { delete(codegenDbDir) } // 매번 빈 DB에서 전체 마이그레이션
}

tasks.jooqCodegen {
    dependsOn(tasks.flywayMigrate)
    inputs.dir(migrations)
    outputs.dir(jooqOutput)
}

sourceSets.main {
    kotlin.srcDir(jooqOutput)
}

tasks.compileKotlin {
    dependsOn(tasks.jooqCodegen)
}
