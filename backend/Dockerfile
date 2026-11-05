# syntax=docker/dockerfile:1
# Build context is the repository root (needs settings.gradle.kts, the wrapper and the version catalog).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY backend ./backend
COPY metamodel ./metamodel
COPY vsm-compiler ./vsm-compiler
ARG GITHUB_ACTOR=token
# The GitHub Packages token is mounted as a build secret, so it never lands in an image layer.
RUN --mount=type=secret,id=github_token,required=true \
    --mount=type=cache,target=/root/.gradle \
    GITHUB_TOKEN="$(cat /run/secrets/github_token)" GITHUB_ACTOR="$GITHUB_ACTOR" \
    sh ./gradlew :backend:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
RUN useradd --system sysarch
COPY --from=build /workspace/backend/build/libs/*.jar /app/sysarch.jar
USER sysarch
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/sysarch.jar"]
