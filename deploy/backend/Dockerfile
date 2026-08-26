FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace/backend

COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY backend/src src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system app && useradd --system --gid app --create-home app
WORKDIR /app

COPY --from=build /workspace/backend/target/museum-meeting-room-backend-*.jar /app/app.jar

ENV LOG_DIR=/app/logs

RUN mkdir -p /app/logs && chown -R app:app /app

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
