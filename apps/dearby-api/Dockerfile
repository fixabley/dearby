FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
RUN ./gradlew --no-daemon bootJar && mv build/libs/*.jar app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app && mkdir -p data/jwt && chown app data/jwt
COPY --from=build /src/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
