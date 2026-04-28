# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:25-jre AS runtime

RUN groupadd --system aubb \
        && useradd --system --gid aubb --create-home --home-dir /opt/aubb aubb

WORKDIR /opt/aubb

COPY --from=build /workspace/target/server-*.jar /opt/aubb/app.jar

ENV SPRING_DOCKER_COMPOSE_ENABLED=false
ENV JAVA_OPTS=""

EXPOSE 8080

USER aubb

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/aubb/app.jar"]
