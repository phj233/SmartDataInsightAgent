FROM azul/zulu-openjdk:21-jdk AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean bootJar -x test -x processAot --no-daemon

FROM azul/zulu-openjdk:21-jdk
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
