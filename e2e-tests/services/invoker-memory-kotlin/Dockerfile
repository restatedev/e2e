# Single-stage image — the shadow JAR is built outside Docker (by gradle) so
# the exact same bytecode is reused for both linux/amd64 and linux/arm64. Only
# the JRE base layer differs per arch.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY build/libs/invoker-memory-kotlin-service.jar app.jar

EXPOSE 9080
CMD ["java", "-jar", "app.jar"]
