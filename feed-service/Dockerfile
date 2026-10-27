FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY common-framework/pom.xml common-framework/pom.xml
COPY common-framework/src common-framework/src
COPY common-framework/mvnw common-framework/mvnw
COPY common-framework/.mvn common-framework/.mvn
RUN chmod +x common-framework/mvnw && ./common-framework/mvnw -f common-framework/pom.xml clean install -DskipTests

COPY feed-service/pom.xml feed-service/pom.xml
COPY feed-service/src feed-service/src
COPY feed-service/mvnw feed-service/mvnw
COPY feed-service/.mvn feed-service/.mvn
RUN chmod +x feed-service/mvnw && ./feed-service/mvnw -f feed-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/feed-service/target/*.jar app.jar

ENV SERVER_PORT=8088
EXPOSE 8088

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
