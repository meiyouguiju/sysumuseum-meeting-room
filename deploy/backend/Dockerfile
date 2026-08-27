FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system app && useradd --system --gid app --create-home app
WORKDIR /app

COPY artifacts/backend/app.jar /app/app.jar

ENV LOG_DIR=/app/logs

RUN mkdir -p /app/logs && chown -R app:app /app

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
