# syntax=docker/dockerfile:1.7

FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY bot/pom.xml bot/pom.xml
COPY bot/src bot/src

RUN mvn -f bot/pom.xml -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/bot/target/bot-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
