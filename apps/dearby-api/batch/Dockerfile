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
RUN useradd --system --uid 10001 app
COPY --from=build /src/app.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
