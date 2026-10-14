# 빌드 컨텍스트는 apps/dearby-api(멀티모듈 루트). compose.prod.yaml: build.context=., dockerfile=api/Dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY core core
COPY api api
RUN ./gradlew --no-daemon :api:bootJar && mv api/build/libs/*.jar app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app && mkdir -p data/jwt && chown app data/jwt
COPY --from=build /src/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
