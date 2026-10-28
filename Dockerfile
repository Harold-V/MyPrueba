# syntax=docker/dockerfile:1
# One image: Spring Boot serves the API and the built frontend on the same origin.
# The build stages run on the builder's own platform (a jar and static files are portable);
# only the runtime stage is per-architecture, so an arm64 image needs no emulation.

# ---- frontend: dependencies before sources, so the npm layer stays cached ----
FROM --platform=$BUILDPLATFORM node:24-bookworm-slim AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- backend: the frontend goes in as classpath:/static ----
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS app
WORKDIR /app
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=web /web/dist src/main/resources/static
# Tests and the coverage gate already ran in CI (scripts/verify.sh) before this build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Djacoco.skip=true -Dspotless.check.skip=true \
    && cp target/myprueba-backend-*.jar /app/app.jar

# ---- runtime: JRE only, non-root, the database on a mounted volume ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=app /app/app.jar app.jar
ENV APP_DB_PATH=/data/myprueba.db \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
VOLUME /data
USER 1000:1000
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
