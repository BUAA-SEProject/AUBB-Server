FROM criyle/go-judge:v1.11.4 AS go-judge

FROM eclipse-temurin:21.0.8_9-jdk-jammy AS java21

FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 ca-certificates g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt

COPY --from=go-judge /opt/go-judge /opt/
COPY --from=java21 /opt/java/openjdk /opt/java/openjdk
COPY docker/go-judge/mount.yaml /opt/mount.yaml

EXPOSE 5050/tcp

ENTRYPOINT ["./go-judge"]
