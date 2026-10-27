FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY common-framework/pom.xml common-framework/pom.xml
COPY common-framework/src common-framework/src
COPY common-framework/mvnw common-framework/mvnw
COPY common-framework/.mvn common-framework/.mvn
RUN chmod +x common-framework/mvnw && ./common-framework/mvnw -f common-framework/pom.xml clean install -DskipTests

COPY auth-service/pom.xml auth-service/pom.xml
COPY auth-service/src auth-service/src
COPY auth-service/mvnw auth-service/mvnw
COPY auth-service/.mvn auth-service/.mvn
RUN chmod +x auth-service/mvnw && ./auth-service/mvnw -f auth-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/auth-service/target/*.jar app.jar

ENV SERVER_PORT=8081
EXPOSE 8081

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
