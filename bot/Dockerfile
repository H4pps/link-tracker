# syntax=docker/dockerfile:1.7

FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY ai-agent/pom.xml ai-agent/pom.xml
COPY bot/pom.xml bot/pom.xml
COPY build-report-aggregate/pom.xml build-report-aggregate/pom.xml
COPY scrapper/pom.xml scrapper/pom.xml

COPY bot/src bot/src

RUN chmod +x mvnw && ./mvnw -pl bot -am -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/bot/target/bot-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
