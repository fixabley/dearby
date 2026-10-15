rootProject.name = "dearby"

// core: 도메인·application·저장소(jOOQ)·마이그레이션. api: 웹·보안(실행 앱). batch: 활동 수집(Spring Batch, 실행 앱).
// 실행 앱마다 Dockerfile을 따로 두고 필요한 모듈만 이미지에 넣으므로, 폴더가 있는 모듈만 포함한다
listOf("core", "api", "batch").filter { file(it).isDirectory }.forEach { include(it) }
