FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

RUN apk add --no-cache maven

COPY common-framework/pom.xml common-framework/pom.xml
COPY common-framework/src common-framework/src
RUN mvn -f common-framework/pom.xml clean install -DskipTests

COPY auth-service/pom.xml auth-service/pom.xml
COPY auth-service/src auth-service/src
RUN mvn -f auth-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/auth-service/target/*.jar app.jar

ENV SERVER_PORT=8081
EXPOSE 8081

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
