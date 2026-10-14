# 빌드 컨텍스트는 apps/dearby-api(멀티모듈 루트). compose.prod.yaml: build.context=., dockerfile=batch/Dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY core core
COPY batch batch
# settings.gradle.kts가 api 모듈도 포함하므로 빈 빌드 파일만 둔다(api 소스는 이미지에 넣지 않는다)
RUN mkdir -p api && touch api/build.gradle.kts && ./gradlew --no-daemon :batch:bootJar && mv batch/build/libs/*.jar app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
# 수집 엔진 cli(COLLECTION_ENGINE=cli)용 Claude Code CLI. Node.js는 공식 node 이미지에서 가져온다
COPY --from=node:24-bookworm-slim /usr/local/bin/node /usr/local/bin/node
COPY --from=node:24-bookworm-slim /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && npm install -g @anthropic-ai/claude-code \
    && npm cache clean --force \
    && useradd --system --uid 10001 --create-home app
COPY --from=build /src/app.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
