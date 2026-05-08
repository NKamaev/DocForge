# ── Stage 1: Build frontend ──────────────────────────────────────────────────
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# ── Stage 2: Build backend ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS backend-build
WORKDIR /app
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

COPY backend/src ./src
RUN ./gradlew bootJar --no-daemon -q

# ── Stage 3: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=backend-build /app/build/libs/docforge.jar app.jar
COPY --from=frontend-build /app/frontend/dist /app/static

ENV SPRING_PROFILES_ACTIVE=docker
ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/static/

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
