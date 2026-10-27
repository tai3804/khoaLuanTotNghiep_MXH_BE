FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

RUN apk add --no-cache maven

COPY eureka-server/pom.xml eureka-server/pom.xml
COPY eureka-server/src eureka-server/src
RUN mvn -f eureka-server/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/eureka-server/target/*.jar app.jar

ENV SERVER_PORT=8761
EXPOSE 8761

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
