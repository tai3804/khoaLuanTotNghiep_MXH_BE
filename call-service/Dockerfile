FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY common-framework/pom.xml common-framework/pom.xml
COPY common-framework/src common-framework/src
COPY common-framework/mvnw common-framework/mvnw
COPY common-framework/.mvn common-framework/.mvn
RUN chmod +x common-framework/mvnw && ./common-framework/mvnw -f common-framework/pom.xml clean install -DskipTests

COPY call-service/pom.xml call-service/pom.xml
COPY call-service/src call-service/src
COPY call-service/mvnw call-service/mvnw
COPY call-service/.mvn call-service/.mvn
RUN chmod +x call-service/mvnw && ./call-service/mvnw -f call-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/call-service/target/*.jar app.jar

ENV SERVER_PORT=8086
EXPOSE 8086

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
