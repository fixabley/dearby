plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jackson") // 추출 결과 JSON 읽기(JsonMapper)
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("com.microsoft.playwright:playwright:1.63.0") // 브라우저 렌더링 수집(admin-v1.md "브라우저 렌더링"). 이미지의 Chromium은 batch/Dockerfile이 설치한다
    // 활동 벡터(native-v1.md "활동 벡터"): 배치 JVM 안에서 ONNX 모델을 돌린다(외부 API 없음). 토크나이저는 HF tokenizer.json을 DJL로 읽는다
    implementation("com.microsoft.onnxruntime:onnxruntime:1.22.0")
    implementation("ai.djl.huggingface:tokenizers:0.33.0")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(testFixtures(project(":core")))
    testImplementation("org.springframework.boot:spring-boot-starter-batch-jdbc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// .env.local 같은 상대 경로는 apps/dearby-api 기준이다
tasks.bootRun {
    workingDir = rootProject.projectDir
    environment("SPRING_PROFILES_ACTIVE", System.getenv("SPRING_PROFILES_ACTIVE") ?: "local")
}
