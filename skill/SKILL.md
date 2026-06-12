# Graduation Thesis — Project Skill

## Tổng quan dự án

- **Tên dự án**: graduation-thesis
- **Group ID**: `iuh.fit`
- **Base package**: `iuh.fit.graduationthesis`
- **Loại**: Spring Boot Monolith (kiến trúc module hóa)
- **Java version**: 21
- **Spring Boot version**: 3.5.14
- **Build tool**: Maven (Maven Wrapper — `mvnw.cmd`)
- **Database**: MariaDB (`auth_db`)
- **ORM**: Hibernate / Spring Data JPA (cấu hình thủ công, tắt AutoConfiguration)
- **Security**: Spring Security + OAuth2 Resource Server + JWT (RSA256)
- **i18n**: Spring MessageSource (hỗ trợ English + Vietnamese)

---

## Cây thư mục dự án

```
graduation-thesis/
├── .env                                    # Biến môi trường (port, DB, JWT, i18n)
├── .gitattributes
├── .gitignore
├── HELP.md
├── mvnw / mvnw.cmd                        # Maven Wrapper
├── pom.xml                                 # Maven POM — quản lý dependencies
├── scripts/                                # (trống — dùng cho scripts tiện ích)
├── skill/                                  # 📌 Folder này — tài liệu dự án
│   ├── SKILL.md                            # File chính bạn đang đọc
│   ├── ARCHITECTURE.md                     # Kiến trúc chi tiết
│   ├── API.md                              # Danh sách API endpoints
│   └── ENV.md                              # Hướng dẫn cấu hình .env
│
└── src/
    ├── main/
    │   ├── java/iuh/fit/graduationthesis/
    │   │   ├── GraduationThesisApplication.java    # Entry point
    │   │   │
    │   │   ├── auth/                               # 🔐 MODULE: Authentication
    │   │   │   ├── configs/
    │   │   │   │   └── AuthConfig.java             # DataSource + JPA config riêng cho auth_db
    │   │   │   ├── controllers/
    │   │   │   │   ├── AuthController.java          # POST /api/v1/auth/register, /login
    │   │   │   │   └── Test.java                    # GET /test — test JWT generation
    │   │   │   ├── dto/
    │   │   │   │   ├── AuthResponse.java            # Response DTO (token, refreshToken, userName)
    │   │   │   │   ├── LoginRequest.java            # Request DTO (userName, password)
    │   │   │   │   └── RegisterRequest.java         # Request DTO (userName, password)
    │   │   │   ├── modules/                         # JPA Entities
    │   │   │   │   ├── Account.java                 # Entity: accounts (UUID, userName, password, roles, permissions, userId)
    │   │   │   │   ├── Permission.java              # Entity: permissions (UUID, name, description)
    │   │   │   │   ├── RefreshToken.java            # Entity: refresh_tokens (UUID, token, accountId, expiresAt)
    │   │   │   │   └── enums/
    │   │   │   │       ├── Role.java                # Enum: ADMIN, USER
    │   │   │   │       └── ValidationMessage.java   # Enum + Constants cho validation messages
    │   │   │   ├── repositories/
    │   │   │   │   ├── AccountRepository.java       # JPA Repository
    │   │   │   │   ├── PermissionRepository.java    # JPA Repository
    │   │   │   │   └── RefreshTokenRepository.java  # JPA Repository
    │   │   │   └── services/
    │   │   │       ├── AuthService.java             # Business logic: register, login
    │   │   │       └── JwtService.java              # JWT token generation (RSA256)
    │   │   │
    │   │   └── common/                              # 🧩 MODULE: Shared/Common
    │   │       ├── configs/
    │   │       │   ├── LocaleConfig.java            # i18n: LocaleResolver + Interceptor
    │   │       │   └── SecurityConfig.java          # Spring Security filter chain
    │   │       ├── dto/
    │   │       │   └── responses/
    │   │       │       └── ApiResponse.java         # Generic API response wrapper
    │   │       ├── exceptions/
    │   │       │   ├── ErrorCode.java               # Enum: tất cả mã lỗi + i18n messageKey
    │   │       │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice — xử lý exception tập trung
    │   │       │   └── exception_types/
    │   │       │       ├── AppException.java        # Abstract base exception
    │   │       │       ├── BusinessException.java   # Lỗi nghiệp vụ (400, 404...)
    │   │       │       ├── ExternalServiceException.java  # Lỗi dịch vụ ngoài (503)
    │   │       │       ├── ForbiddenException.java  # 403
    │   │       │       ├── ResourceNotFoundException.java # 404
    │   │       │       └── UnauthorizedException.java     # 401
    │   │       ├── middlewares/
    │   │       │   └── JwtIpValidationFilter.java   # Filter chống token bị lộ (so sánh IP)
    │   │       └── utils/
    │   │           └── JwtUtil.java                 # RSA Key loader + JwtEncoder/Decoder beans
    │   │
    │   └── resources/
    │       ├── application.yaml                     # Config chính (server, DB, JWT, i18n)
    │       ├── certs/
    │       │   ├── private_key.pem                  # RSA Private Key
    │       │   └── public_key.pem                   # RSA Public Key
    │       └── i18n/
    │           ├── messages.properties              # Default messages (English fallback)
    │           ├── messages_en.properties            # English messages
    │           └── messages_vi.properties            # Vietnamese messages
    │
    └── test/
        └── java/iuh/fit/graduationthesis/
            └── DemoApplicationTests.java            # Test mặc định
```

---

## Tech Stack & Dependencies (pom.xml)

| Dependency | Mục đích |
|---|---|
| `spring-boot-starter-web` | REST API, MVC |
| `spring-boot-starter-oauth2-resource-server` | JWT validation (Nimbus JOSE) |
| `spring-boot-starter-data-jpa` | JPA/Hibernate ORM |
| `spring-boot-starter-validation` | Bean Validation (`@Valid`, `@NotBlank`...) |
| `mariadb-java-client` | MariaDB JDBC Driver |
| `bcpkix-jdk18on` (BouncyCastle) | Hỗ trợ đọc PEM RSA keys |
| `lombok` | Giảm boilerplate code |
| `spring-boot-starter-test` | Unit testing |

---

## Conventions & Patterns

### 1. Cấu trúc Module
- Mỗi module (`auth`, `common`) có cấu trúc riêng: `configs/`, `controllers/`, `dto/`, `modules/`, `repositories/`, `services/`
- Module `common` chứa các thành phần dùng chung: exceptions, security, utils, i18n

### 2. Lombok Patterns
- Dùng `@RequiredArgsConstructor` + `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)` để inject dependencies qua constructor
- Entities dùng `@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor`

### 3. Exception Handling
- Tất cả exception kế thừa `AppException` (abstract)
- `ErrorCode` enum chứa `HttpStatus` + `messageKey` (i18n key trong file `.properties`)
- `GlobalExceptionHandler` xử lý tập trung, resolve message qua `MessageSource`

### 4. API Response Format
```json
{
  "success": true/false,
  "status": 200,
  "error": "ERROR_CODE_NAME",
  "message": "Localized message",
  "path": "/api/v1/...",
  "data": { ... },
  "validationErrors": { "field": "error message" },
  "timestamp": "2026-06-12T04:00:00Z"
}
```

### 5. i18n (Internationalization)
- Client gửi header `Accept-Language: vi` hoặc `Accept-Language: en`
- Mặc định: English (cấu hình qua `APP_DEFAULT_LOCALE` trong `.env`)
- File messages: `src/main/resources/i18n/messages_xx.properties`
- Spring Boot tự cấu hình `MessageSource` từ `spring.messages.*` trong `application.yaml`

### 6. Security Flow
```
Request → JwtIpValidationFilter → SecurityFilterChain (OAuth2 JWT) → Controller
```
- Public endpoints: `/api/v1/auth/**`, `/test`, `/error`
- Token chứa: `subject` (userId), `roles`, `permissions`, `ip`
- Filter kiểm tra IP trong token vs IP thực tế → chống token bị đánh cắp

### 7. JWT Architecture
- **Signing**: RSA256 (asymmetric) — Private Key ký, Public Key verify
- **Access Token**: JWT signed, expire = 5 phút (configurable)
- **Refresh Token**: UUID random, lưu DB, expire = 7 ngày (configurable)
- **Key loading**: Đọc từ PEM files, cache trong RAM qua `@PostConstruct`

### 8. Database
- Tắt `DataSourceAutoConfiguration` và `HibernateJpaAutoConfiguration`
- Cấu hình DataSource thủ công trong `AuthConfig.java` (hỗ trợ multi-database sau này)
- Hibernate `ddl-auto: update` (tự tạo/cập nhật bảng)
- Tables: `accounts`, `account_roles`, `permissions`, `account_permissions`, `refresh_tokens`

---

## Cách chạy dự án

```bash
# 1. Cấu hình biến môi trường (sửa file .env hoặc set system env)
# 2. Đảm bảo MariaDB đang chạy trên localhost:3306
# 3. Chạy project
./mvnw.cmd spring-boot:run
```

---

## Lưu ý quan trọng

- `JAVA_HOME` phải được set đúng để chạy Maven Wrapper
- File `.env` chỉ được IntelliJ IDEA/IDE đọc (cần plugin EnvFile hoặc cấu hình Run Configuration)
- Khi thêm ErrorCode mới, **phải thêm key tương ứng** vào cả 3 file: `messages.properties`, `messages_en.properties`, `messages_vi.properties`
- Khi thêm module mới (ví dụ: `booking`), cần tạo `XxxConfig.java` riêng cho DataSource/JPA config
