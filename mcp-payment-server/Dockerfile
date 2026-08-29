# syntax=docker/dockerfile:1
# Build from the repository ROOT (the parent POM is needed):
#   docker build -f mcp-payment-server/Dockerfile -t dra/mcp-payment-server .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# POMs first: while they do not change, the dependency layer is reused
COPY pom.xml .
COPY mcp-payment-server/pom.xml mcp-payment-server/
COPY dispute-agent/pom.xml dispute-agent/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl mcp-payment-server -am dependency:go-offline -DskipTests

COPY mcp-payment-server/src mcp-payment-server/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl mcp-payment-server -am package -DskipTests \
 && cp mcp-payment-server/target/mcp-payment-server-*.jar /src/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 --create-home mcp
USER mcp

COPY --from=build /src/app.jar app.jar

EXPOSE 8081
ENV MCP_SERVER_PORT=8081

# No HEALTHCHECK here: compose carries it, so the probe and the agent start condition are defined
# in the same place
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
