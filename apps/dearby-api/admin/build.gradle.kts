plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":auth"))
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(testFixtures(project(":core")))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// .env.local 같은 상대 경로는 apps/dearby-api 기준이다
tasks.bootRun {
    workingDir = rootProject.projectDir
    environment("SPRING_PROFILES_ACTIVE", System.getenv("SPRING_PROFILES_ACTIVE") ?: "local")
}
