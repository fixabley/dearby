# 빌드 컨텍스트는 apps/dearby-api(멀티모듈 루트). compose.prod.yaml: build.context=., dockerfile=batch/Dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY core core
COPY batch batch
RUN ./gradlew --no-daemon :batch:bootJar && mv batch/build/libs/*.jar app.jar

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
# 브라우저 렌더링 수집(admin-v1.md "브라우저 렌더링"): Playwright가 쓰는 Chromium을 이미지에 넣는다. 모든 사용자가 읽을 수 있는 경로에 둔다
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
# 실행 시 Playwright가 브라우저를 다시 내려받으려 하지 않게 한다(2026-10-19: 첫 렌더링에서 install 프로세스를 기다리며 멈추던 문제)
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
# 수집 엔진 cli(COLLECTION_ENGINE=cli)용 Claude Code CLI. Node.js는 공식 node 이미지에서 가져온다
COPY --from=node:24-bookworm-slim /usr/local/bin/node /usr/local/bin/node
COPY --from=node:24-bookworm-slim /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && npm install -g @anthropic-ai/claude-code \
    && npm cache clean --force \
    && useradd --system --uid 10001 --create-home app
COPY --from=build /src/app.jar app.jar
# Playwright CLI는 부트 jar 안의 playwright 라이브러리로 실행한다(PropertiesLauncher). --with-deps가 Chromium 런타임 패키지를 apt로 설치한다
RUN java -cp app.jar -Dloader.main=com.microsoft.playwright.CLI org.springframework.boot.loader.launch.PropertiesLauncher install --with-deps chromium \
    && chmod -R a+rX /ms-playwright \
    && rm -rf /var/lib/apt/lists/*
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
