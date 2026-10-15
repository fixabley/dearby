rootProject.name = "dearby"

// core: 도메인·application·저장소(jOOQ)·마이그레이션. api: 웹·보안(실행 앱). 실행 앱마다 Dockerfile을 따로 둔다
include("core", "api")
