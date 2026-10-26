plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":auth"))
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-mail") // 이메일 인증번호(native-v1.md "연락처 인증")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
    runtimeOnly("org.xerial:sqlite-jdbc")
    testImplementation(testFixtures(project(":core")))
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    // 테스트용 패스키 인증기. 버전은 spring-security-webauthn이 쓰는 webauthn4j-core와 맞춘다
    testImplementation("com.webauthn4j:webauthn4j-test:0.31.9.RELEASE")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 로컬 실행은 local 프로필(application-local.yaml). SPRING_PROFILES_ACTIVE로 바꿀 수 있다
// .env.local·data/ 같은 상대 경로는 apps/dearby-api 기준이다
tasks.bootRun {
    workingDir = rootProject.projectDir
    environment("SPRING_PROFILES_ACTIVE", System.getenv("SPRING_PROFILES_ACTIVE") ?: "local")
}
