FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY common-framework/pom.xml common-framework/pom.xml
COPY common-framework/src common-framework/src
COPY common-framework/mvnw common-framework/mvnw
COPY common-framework/.mvn common-framework/.mvn
RUN chmod +x common-framework/mvnw && ./common-framework/mvnw -f common-framework/pom.xml clean install -DskipTests

COPY post-service/pom.xml post-service/pom.xml
COPY post-service/src post-service/src
COPY post-service/mvnw post-service/mvnw
COPY post-service/.mvn post-service/.mvn
RUN chmod +x post-service/mvnw && ./post-service/mvnw -f post-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/post-service/target/*.jar app.jar

ENV SERVER_PORT=8082
EXPOSE 8082

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
