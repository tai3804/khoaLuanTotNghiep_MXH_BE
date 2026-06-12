# ==========================================
# 🔨 Stage 1: Development (Hot-reload)
# Dùng cho: docker compose up (dev mode)
# ==========================================
FROM eclipse-temurin:21-jdk AS dev

WORKDIR /app

# Copy Maven wrapper trước (cache layer)
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN chmod +x mvnw

# Copy pom.xml trước để cache dependencies
COPY pom.xml .
RUN ./mvnw dependency:resolve -B -q

# Copy toàn bộ source code
COPY src src

# Expose port
EXPOSE 8080

# Chạy Spring Boot với DevTools active (hỗ trợ hot-reload)
# -Dspring-boot.run.jvmArguments: Buộc DevTools poll file thay đổi (cần cho Docker volume mount)
ENTRYPOINT ["./mvnw", "spring-boot:run", \
    "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.poll-interval=2000 -Dspring.devtools.restart.quiet-period=1000"]


# ==========================================
# 📦 Stage 2: Build JAR (Production)
# ==========================================
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN chmod +x mvnw

COPY pom.xml .
RUN ./mvnw dependency:resolve -B -q

COPY src src

# Build production JAR (bỏ qua tests để tăng tốc)
RUN ./mvnw clean package -DskipTests -B -q


# ==========================================
# 🚀 Stage 3: Production Runtime
# Dùng cho: production deployment
# ==========================================
FROM eclipse-temurin:21-jre AS prod

WORKDIR /app

# Copy JAR từ build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
