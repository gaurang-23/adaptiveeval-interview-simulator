FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk-alpine
RUN addgroup -g 10002 -S runner && \
    adduser -u 10002 -S runner -G runner && \
    mkdir -p /tmp/runner-sandbox && \
    chown -R 10002:10002 /tmp/runner-sandbox
WORKDIR /app
COPY --from=build /app/target/runner-service-1.0.0.jar app.jar
USER 10002:10002
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
