# Kiến trúc hệ thống — Chi tiết

## Tổng quan kiến trúc

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (Frontend/Postman)                │
│                  Header: Accept-Language: vi/en                 │
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTP Request
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT APPLICATION                      │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              MIDDLEWARE / FILTER LAYER                     │  │
│  │                                                           │  │
│  │  1. LocaleChangeInterceptor (?lang=vi)                    │  │
│  │  2. AcceptHeaderLocaleResolver (Accept-Language header)    │  │
│  │  3. JwtIpValidationFilter (IP vs Token IP check)          │  │
│  │  4. SecurityFilterChain (OAuth2 JWT validation)           │  │
│  └───────────────────────────────────────────────────────────┘  │
│                          │                                      │
│                          ▼                                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              CONTROLLER LAYER                             │  │
│  │                                                           │  │
│  │  AuthController  ── POST /api/v1/auth/register            │  │
│  │                  ── POST /api/v1/auth/login               │  │
│  │  Test            ── GET  /test                            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                          │                                      │
│                          ▼                                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              SERVICE LAYER                                │  │
│  │                                                           │  │
│  │  AuthService ── register(), login()                       │  │
│  │  JwtService  ── generateToken() (RSA256 signing)          │  │
│  └───────────────────────────────────────────────────────────┘  │
│                          │                                      │
│                          ▼                                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              REPOSITORY LAYER (JPA)                       │  │
│  │                                                           │  │
│  │  AccountRepository      ── findByUserName, existsByUserName│  │
│  │  PermissionRepository   ── findByName                     │  │
│  │  RefreshTokenRepository ── findByToken, deleteByAccountId │  │
│  └───────────────────────────────────────────────────────────┘  │
│                          │                                      │
│                          ▼                                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              DATABASE (MariaDB)                           │  │
│  │                                                           │  │
│  │  auth_db:                                                 │  │
│  │    ├── accounts              (id, userName, password, userId)│ │
│  │    ├── account_roles         (account_id, roles)          │  │
│  │    ├── permissions           (id, name, description)      │  │
│  │    ├── account_permissions   (account_id, permission_id)  │  │
│  │    └── refresh_tokens        (id, token, accountId, ...)  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              CROSS-CUTTING CONCERNS                       │  │
│  │                                                           │  │
│  │  GlobalExceptionHandler ── Xử lý exception tập trung     │  │
│  │  MessageSource (i18n)   ── Dịch message theo locale       │  │
│  │  ApiResponse            ── Format response thống nhất     │  │
│  │  JwtUtil                ── RSA Key loader + Encoder/Decoder│ │
│  │  ErrorCode              ── Enum mã lỗi + i18n key        │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Chi tiết từng file

### 📁 Root

| File | Mô tả |
|---|---|
| `GraduationThesisApplication.java` | Entry point. Tắt `DataSourceAutoConfiguration` và `HibernateJpaAutoConfiguration` để cấu hình thủ công |

---

### 📁 auth/configs/

| File | Mô tả |
|---|---|
| `AuthConfig.java` | Cấu hình DataSource (`HikariDataSource`), `EntityManagerFactory`, `TransactionManager` riêng cho module auth. Scan entities trong `auth.modules`, scan repositories trong `auth.repositories` |

**Lý do cấu hình thủ công**: Hỗ trợ multi-database trong tương lai. Mỗi module sẽ có DataSource riêng.

---

### 📁 auth/controllers/

| File | Mô tả |
|---|---|
| `AuthController.java` | 2 endpoints: `POST /register` và `POST /login`. Login trả Access Token trong body, Refresh Token trong HttpOnly Cookie |
| `Test.java` | Endpoint test: `GET /test` — sinh JWT token demo |

**Login Flow chi tiết:**
1. Validate `LoginRequest` (`@Valid`)
2. Tìm Account theo userName
3. So sánh password (BCrypt)
4. Sinh Access Token (JWT RSA256) + Refresh Token (UUID)
5. Lưu Refresh Token vào DB
6. Set Refresh Token vào Cookie (`HttpOnly`, `Secure`, `SameSite=Strict`)
7. Xóa Refresh Token khỏi response body → chỉ trả Access Token

---

### 📁 auth/dto/

| File | Fields | Validation |
|---|---|---|
| `RegisterRequest.java` | userName, password | `@NotBlank`, `@Size(min=3)`, `@Size(min=8)` |
| `LoginRequest.java` | userName, password | `@NotBlank`, `@Size(min=3)`, `@Size(min=8)` |
| `AuthResponse.java` | token, refreshToken, userName | `@JsonInclude(NON_NULL)` |

---

### 📁 auth/modules/ (JPA Entities)

| Entity | Table | Quan hệ |
|---|---|---|
| `Account` | `accounts` | `roles` → `@ElementCollection` (account_roles), `permissions` → `@ManyToMany` (account_permissions) |
| `Permission` | `permissions` | Được tham chiếu từ Account |
| `RefreshToken` | `refresh_tokens` | Liên kết accountId (không dùng FK, chỉ lưu UUID) |
| `Role` (enum) | — | `ADMIN`, `USER` |
| `ValidationMessage` (enum) | — | Constants cho validation messages |

---

### 📁 auth/services/

| File | Methods | Mô tả |
|---|---|---|
| `AuthService.java` | `register()`, `login()` | Business logic chính. Sử dụng `@Transactional("authTransactionManager")` |
| `JwtService.java` | `generateToken()` | Ký JWT bằng RSA256 (Nimbus JOSE). Claims: sub, roles, permissions, ip, iss, iat, exp |

---

### 📁 common/configs/

| File | Mô tả |
|---|---|
| `SecurityConfig.java` | Spring Security filter chain. Public endpoints: `/api/v1/auth/**`, `/test`, `/error`. OAuth2 Resource Server + JWT decoder. BCrypt PasswordEncoder |
| `LocaleConfig.java` | i18n config. `AcceptHeaderLocaleResolver` (mặc định đọc từ `app.locale.default`). `LocaleChangeInterceptor` (param `?lang=`). MessageSource auto-configured |

---

### 📁 common/exceptions/

| File | Mô tả |
|---|---|
| `ErrorCode.java` | Enum chứa tất cả mã lỗi. Mỗi entry: `HttpStatus` + `messageKey` (key trong `messages_xx.properties`) |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice`. Xử lý: `AppException`, `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `Exception`. Inject `MessageSource` để resolve i18n message |
| `AppException.java` | Abstract base exception. Chứa `ErrorCode` |
| `BusinessException.java` | Lỗi nghiệp vụ (USER_EXISTED, INVALID_CREDENTIALS...) |
| `ResourceNotFoundException.java` | Lỗi tài nguyên không tìm thấy |
| `ForbiddenException.java` | Lỗi 403 |
| `UnauthorizedException.java` | Lỗi 401 |
| `ExternalServiceException.java` | Lỗi dịch vụ bên ngoài |

**Hierarchy:**
```
RuntimeException
  └── AppException (abstract) ← chứa ErrorCode
        ├── BusinessException
        ├── ResourceNotFoundException
        ├── ForbiddenException
        ├── UnauthorizedException
        └── ExternalServiceException
```

---

### 📁 common/middlewares/

| File | Mô tả |
|---|---|
| `JwtIpValidationFilter.java` | `OncePerRequestFilter`. Lấy IP client (hỗ trợ X-Forwarded-For). So sánh IP trong JWT claim `client_ip` với IP thực tế. Nếu khác → trả 403 |

---

### 📁 common/utils/

| File | Mô tả |
|---|---|
| `JwtUtil.java` | Singleton utility. Load RSA keys từ PEM (classpath) qua `@PostConstruct`. Cung cấp `JwtDecoder` + `JwtEncoder` beans. Sinh Refresh Token (UUID random) |

---

### 📁 resources/

| File | Mô tả |
|---|---|
| `application.yaml` | Config: server port, spring.messages (i18n), app.locale.default, app.datasource.auth, jwt config |
| `certs/private_key.pem` | RSA Private Key cho JWT signing |
| `certs/public_key.pem` | RSA Public Key cho JWT verification |
| `i18n/messages.properties` | Default messages (English fallback) |
| `i18n/messages_en.properties` | English messages |
| `i18n/messages_vi.properties` | Vietnamese messages (UTF-8) |
