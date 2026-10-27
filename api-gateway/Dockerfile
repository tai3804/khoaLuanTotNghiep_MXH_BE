FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY api-gateway/pom.xml api-gateway/pom.xml
COPY api-gateway/src api-gateway/src
COPY api-gateway/mvnw api-gateway/mvnw
COPY api-gateway/.mvn api-gateway/.mvn
RUN chmod +x api-gateway/mvnw && ./api-gateway/mvnw -f api-gateway/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/api-gateway/target/*.jar app.jar

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
