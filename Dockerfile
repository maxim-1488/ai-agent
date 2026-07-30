FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY config ./config
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon shadowJar

FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
RUN useradd --system --create-home aiagent

WORKDIR /app
COPY --from=build /workspace/build/libs/*-all.jar /app/ai-agent.jar

USER aiagent
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/ai-agent.jar"]
