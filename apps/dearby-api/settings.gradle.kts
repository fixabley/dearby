rootProject.name = "dearby"

// core: 도메인·application·저장소(jOOQ)·마이그레이션. api: 공개 웹·보안(실행 앱). batch: 활동 수집(Spring Batch, 실행 앱).
// auth: 패스키 로그인·토큰 발급과 검증·보안 기본 설정(api·admin이 함께 씀). admin: 관리자 API(실행 앱, shared/contracts/admin-v1.md).
// 실행 앱마다 Dockerfile을 따로 두고 필요한 모듈만 이미지에 넣으므로, 폴더가 있는 모듈만 포함한다
listOf("core", "auth", "api", "batch", "admin").filter { file(it).isDirectory }.forEach { include(it) }
