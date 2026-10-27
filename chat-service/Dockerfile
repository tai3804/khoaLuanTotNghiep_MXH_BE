FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY common-framework/pom.xml common-framework/pom.xml
COPY common-framework/src common-framework/src
COPY common-framework/mvnw common-framework/mvnw
COPY common-framework/.mvn common-framework/.mvn
RUN chmod +x common-framework/mvnw && ./common-framework/mvnw -f common-framework/pom.xml clean install -DskipTests

COPY chat-service/pom.xml chat-service/pom.xml
COPY chat-service/src chat-service/src
COPY chat-service/mvnw chat-service/mvnw
COPY chat-service/.mvn chat-service/.mvn
RUN chmod +x chat-service/mvnw && ./chat-service/mvnw -f chat-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/chat-service/target/*.jar app.jar

ENV SERVER_PORT=8087
EXPOSE 8087

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
