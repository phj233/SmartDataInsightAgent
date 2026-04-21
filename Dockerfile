FROM ghcr.io/graalvm/jdk-community:21 AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean bootJar -x test -x processAot --no-daemon

FROM ghcr.io/graalvm/jdk-community:21
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
