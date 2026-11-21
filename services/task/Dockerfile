# syntax=docker/dockerfile:1.7

# ---- Build: compile and package the Spring Boot jar ----
FROM eclipse-temurin:25-jdk-noble@sha256:4ddc1a8bdaf283421df5487411aff10b797ccf1e06a339041005114c8f489b5c AS build
WORKDIR /workspace

# Dependencies first, so source changes don't invalidate the dependency layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml lombok.config checkstyle.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests, lint and formatting run in CI before the image is built.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -DskipTests -Dcheckstyle.skip -Dspotless.check.skip \
 && java -Djarmode=tools -jar target/task-*.jar extract --layers --launcher \
      --destination target/extracted

# ---- Runtime: JRE only, non-root, one layer per change frequency ----
FROM eclipse-temurin:25-jre-noble@sha256:f3dd0838e69b881cc382237d5901e74ae3705b1056ad964b2e0e523b174fef99

# The image tag is the commit SHA (CI passes it); /info shows it.
ARG GIT_COMMIT_SHA=unknown
ENV GIT_COMMIT_SHA=${GIT_COMMIT_SHA} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app

COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER app
# 8080: API, 8081: management (metrics, health) - not exposed through the ingress.
EXPOSE 8080 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
