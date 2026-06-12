# Hướng dẫn cấu hình Environment (.env)

## Vị trí file
```
graduation-thesis/.env
```

## Danh sách biến môi trường

### Server

| Biến | Giá trị mặc định | Mô tả | Dùng trong |
|---|---|---|---|
| `SERVER_PORT` | `8080` | Cổng chạy server | `application.yaml` → `server.port` |

---

### JWT (JSON Web Token)

| Biến | Giá trị mặc định | Mô tả | Dùng trong |
|---|---|---|---|
| `JWT_PRIVATE_KEY_PATH` | `certs/private_key.pem` | Đường dẫn tới RSA Private Key (classpath) | `application.yaml` → `jwt.private-key-path` |
| `JWT_PUBLIC_KEY_PATH` | `certs/public_key.pem` | Đường dẫn tới RSA Public Key (classpath) | `application.yaml` → `jwt.public-key-path` |
| `JWT_EXPIRATION_MINUTE` | `5` | Thời hạn Access Token (phút) | `application.yaml` → `jwt.expiration-minute` |
| `JWT_REFRESH_TOKEN_EXPIRATION_DAY` | `7` | Thời hạn Refresh Token (ngày) | `application.yaml` → `jwt.refresh-token-expiration-day` |

---

### Database (Auth Module)

| Biến | Giá trị mặc định | Mô tả | Dùng trong |
|---|---|---|---|
| `AUTH_DB_URL` | `jdbc:mariadb://localhost:3306/auth_db?createDatabaseIfNotExist=true` | JDBC URL kết nối MariaDB | `application.yaml` → `app.datasource.auth.jdbc-url` |
| `AUTH_DB_USERNAME` | `root` | Database username | `application.yaml` → `app.datasource.auth.username` |
| `AUTH_DB_PASSWORD` | `root` | Database password | `application.yaml` → `app.datasource.auth.password` |
| `AUTH_DB_DRIVER` | `org.mariadb.jdbc.Driver` | JDBC Driver class | Tham khảo (dùng `AUTH_DB_DRIVER_CLASS_NAME` trong yaml) |

> **Lưu ý:** Trong `application.yaml`, biến `driver-class-name` dùng key `AUTH_DB_DRIVER_CLASS_NAME` (có suffix `_CLASS_NAME`). Nếu muốn thống nhất, hãy đổi `.env` thành `AUTH_DB_DRIVER_CLASS_NAME`.

---

### Internationalization (i18n)

| Biến | Giá trị mặc định | Mô tả | Dùng trong |
|---|---|---|---|
| `APP_DEFAULT_LOCALE` | `en` | Ngôn ngữ mặc định (`en` hoặc `vi`) | `application.yaml` → `app.locale.default` |

**Giá trị hợp lệ:**
- `en` — English (mặc định)
- `vi` — Tiếng Việt

---

## File .env hiện tại

```env
#server
SERVER_PORT=8080

#jwt
private_key_path=certs/private_key.pem
public_key_path=certs/public_key.pem
JWT_EXPIRATION_MINUTE=5

#auth
AUTH_DB_URL=jdbc:mariadb://localhost:3306/auth_db?createDatabaseIfNotExist=true
AUTH_DB_USERNAME=root
AUTH_DB_PASSWORD=root
AUTH_DB_DRIVER=org.mariadb.jdbc.Driver

#i18n - Ngôn ngữ mặc định (en hoặc vi)
APP_DEFAULT_LOCALE=en
```

---

## Mapping: .env → application.yaml

```yaml
server:
  port: ${SERVER_PORT:8080}                              # ← SERVER_PORT

spring:
  messages:
    basename: i18n/messages                              # (hardcoded, không dùng env)
    encoding: UTF-8
    cache-duration: 3600
    use-code-as-default-message: true

app:
  locale:
    default: ${APP_DEFAULT_LOCALE:en}                    # ← APP_DEFAULT_LOCALE
  datasource:
    auth:
      jdbc-url: ${AUTH_DB_URL:...}                       # ← AUTH_DB_URL
      username: ${AUTH_DB_USERNAME:root}                  # ← AUTH_DB_USERNAME
      password: ${AUTH_DB_PASSWORD:root}                  # ← AUTH_DB_PASSWORD
      driver-class-name: ${AUTH_DB_DRIVER_CLASS_NAME:...} # ← AUTH_DB_DRIVER_CLASS_NAME

jwt:
  private-key-path: ${JWT_PRIVATE_KEY_PATH:certs/...}    # ← JWT_PRIVATE_KEY_PATH
  public-key-path: ${JWT_PUBLIC_KEY_PATH:certs/...}      # ← JWT_PUBLIC_KEY_PATH
  expiration-minute: ${JWT_EXPIRATION_MINUTE:5}           # ← JWT_EXPIRATION_MINUTE
  refresh-token-expiration-day: ${JWT_REFRESH_TOKEN_EXPIRATION_DAY:7} # ← JWT_REFRESH_TOKEN_EXPIRATION_DAY
```

---

## Cách dùng trong IntelliJ IDEA

1. Cài plugin **EnvFile** (Marketplace)
2. Mở **Run/Debug Configurations**
3. Tab **EnvFile** → ✅ Enable → Add file `.env`
4. Chạy project

Hoặc set biến hệ thống:
```powershell
$env:SERVER_PORT = "9090"
$env:APP_DEFAULT_LOCALE = "vi"
./mvnw.cmd spring-boot:run
```
