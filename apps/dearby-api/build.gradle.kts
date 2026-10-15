// Flyway Gradle 플러그인은 DB 지원 모듈을 빌드 스크립트 클래스패스에서만 찾는다
buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:12.4.0")
    }
}

plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.flywaydb.flyway") version "12.4.0" apply false
    id("org.jooq.jooq-codegen-gradle") version "3.21.7" apply false
}

subprojects {
    group = "io.wid"
    version = "0.0.1-SNAPSHOT"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "io.spring.dependency-management")

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
            mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.1")
        }
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        compilerOptions {
            // 생성자 매개변수 이름을 남긴다(Jackson·Spring 바인딩). Spring Boot 플러그인을 쓰지 않는 core에도 필요
            javaParameters = true
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        }
    }

    // 모듈 테스트는 모두 같은 테스트 DB(postgres-test)를 비우고 다시 쓰므로 동시에 돌리지 않는다
    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
