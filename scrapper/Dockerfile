# syntax=docker/dockerfile:1.7

FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY messaging/pom.xml messaging/pom.xml
COPY messaging/src messaging/src
COPY scrapper/pom.xml scrapper/pom.xml
COPY migrations migrations
COPY scrapper/src scrapper/src

RUN mvn -N -DskipTests install \
    && mvn -f messaging/pom.xml -DskipTests install \
    && mvn -f scrapper/pom.xml -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/scrapper/target/scrapper-*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
