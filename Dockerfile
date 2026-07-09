# syntax=docker/dockerfile:1.7

# ------------------------------------------------------------------
# Stage 1 — Build the React frontend
# ------------------------------------------------------------------
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ------------------------------------------------------------------
# Stage 2 — Build the Spring Boot backend + bundle the SPA into it
# ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY backend/pom.xml ./backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f backend/pom.xml -DskipTests dependency:go-offline
COPY backend/ ./backend/
# Copy the built React SPA into Spring Boot's classpath so it is served at /
COPY --from=frontend /app/frontend/dist/ ./backend/src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f backend/pom.xml -DskipTests package

# ------------------------------------------------------------------
# Stage 3 — Runtime
# ------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=backend /app/backend/target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
