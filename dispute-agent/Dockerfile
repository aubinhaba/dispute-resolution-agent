# syntax=docker/dockerfile:1
# Build from the repository ROOT (the parent POM is needed):
#   docker build -f dispute-agent/Dockerfile -t dra/dispute-agent .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# POMs first: while they do not change, the dependency layer is reused
COPY pom.xml .
COPY mcp-payment-server/pom.xml mcp-payment-server/
COPY dispute-agent/pom.xml dispute-agent/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl dispute-agent -am dependency:go-offline -DskipTests

COPY dispute-agent/src dispute-agent/src
COPY mcp-payment-server/src mcp-payment-server/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl dispute-agent -am package -DskipTests \
 && cp dispute-agent/target/dispute-agent-*-boot.jar /src/app.jar

FROM eclipse-temurin:21-jre AS model
ADD https://media.githubusercontent.com/media/spring-projects/spring-ai/refs/heads/main/models/spring-ai-transformers/src/main/resources/onnx/all-MiniLM-L6-v2/model.onnx \
    /model/all-MiniLM-L6-v2/model.onnx

RUN chmod -R a+rX /model \
 && test "$(stat -c%s /model/all-MiniLM-L6-v2/model.onnx)" -gt 50000000 \
 || (echo "model.onnx too small: Git LFS pointer instead of the model" && exit 1)

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 --create-home agent
USER agent

COPY --from=build /src/app.jar app.jar
COPY --from=model /model /opt/model

EXPOSE 8080
ENV DRA_SERVER_PORT=8080 \
    DRA_MCP_PAYMENTS_URL=http://mcp-payment-server:8081 \
    SPRING_AI_EMBEDDING_TRANSFORMER_ONNX_MODEL_URI=file:/opt/model/all-MiniLM-L6-v2/model.onnx

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
