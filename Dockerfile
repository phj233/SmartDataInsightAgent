FROM bellsoft/liberica-runtime-container:jdk-21-glibc AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean bootJar -x test -x processAot --no-daemon

FROM bellsoft/liberica-runtime-container:jdk-21-glibc
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
