# Backend image for Render, which has no Java runtime of its own (decision #30).
# Build from the repo root: the Maven build reads ../api/openapi.yaml (decision #3).
#   docker build -f backend/Dockerfile -t praying-mantis-backend .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app/backend

# Dependencies first, in their own layer: it's only rebuilt when pom.xml changes
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN ./mvnw -B -ntp dependency:go-offline

COPY api /app/api
COPY backend/src src
# Tests need Docker for Testcontainers and already ran in CI (backend.yml)
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system app
USER app
COPY --from=build /app/backend/target/*.jar app.jar
# The JVM takes a quarter of the container's memory by default; Render's free plan has 512 MB
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
