FROM criyle/go-judge:v1.11.4 AS go-judge

FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 ca-certificates openjdk-17-jdk-headless g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt

COPY --from=go-judge /opt/go-judge /opt/mount.yaml /opt/

EXPOSE 5050/tcp

ENTRYPOINT ["./go-judge"]
