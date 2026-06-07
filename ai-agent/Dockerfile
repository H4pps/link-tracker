# syntax=docker/dockerfile:1.7

FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY messaging/pom.xml messaging/pom.xml
COPY messaging/src messaging/src
COPY ai-agent/pom.xml ai-agent/pom.xml
COPY ai-agent/src ai-agent/src

RUN mvn -N -DskipTests install \
    && mvn -f messaging/pom.xml -DskipTests install \
    && mvn -f ai-agent/pom.xml -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/ai-agent/target/ai-agent-*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
