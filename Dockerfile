FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
RUN useradd --system --create-home aiagent

WORKDIR /app
COPY build/libs/*-all.jar /app/ai-agent.jar

USER aiagent
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/ai-agent.jar"]
