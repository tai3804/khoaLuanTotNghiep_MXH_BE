FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

RUN apk add --no-cache maven

COPY common-framework/pom.xml common-framework/pom.xml
COPY common-framework/src common-framework/src
RUN mvn -f common-framework/pom.xml clean install -DskipTests

COPY ai-service/pom.xml ai-service/pom.xml
COPY ai-service/src ai-service/src
RUN mvn -f ai-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/ai-service/target/*.jar app.jar

ENV SERVER_PORT=8091
EXPOSE 8091

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
