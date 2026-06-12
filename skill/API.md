# API Endpoints

## Base URL
```
http://localhost:8080
```

---

## 🔓 Public Endpoints (không cần token)

### 1. Đăng ký tài khoản

```
POST /api/v1/auth/register
```

**Request Body:**
```json
{
  "userName": "john_doe",
  "password": "12345678"
}
```

**Validation Rules:**
| Field | Rule |
|---|---|
| userName | `@NotBlank`, `@Size(min=3)` |
| password | `@NotBlank`, `@Size(min=8)` |

**Response thành công (200):**
```json
{
  "success": true,
  "status": 200,
  "message": "Registration successful",
  "data": {
    "userName": "john_doe"
  },
  "timestamp": "2026-06-12T04:00:00Z"
}
```

**Response lỗi — User đã tồn tại (400):**
```json
{
  "success": false,
  "status": 400,
  "error": "USER_EXISTED",
  "message": "User already exists",
  "path": "/api/v1/auth/register",
  "timestamp": "2026-06-12T04:00:00Z"
}
```

---

### 2. Đăng nhập

```
POST /api/v1/auth/login
```

**Request Body:**
```json
{
  "userName": "john_doe",
  "password": "12345678"
}
```

**Response thành công (200):**
```json
{
  "success": true,
  "status": 200,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJSUzI1NiIsInR...",
    "userName": "john_doe"
  },
  "timestamp": "2026-06-12T04:00:00Z"
}
```

> **Lưu ý:** Refresh Token được gửi qua `Set-Cookie` header (HttpOnly, Secure, SameSite=Strict), KHÔNG nằm trong response body.

**Response lỗi — Sai username/password (401):**
```json
{
  "success": false,
  "status": 401,
  "error": "INVALID_CREDENTIALS",
  "message": "Invalid username or password",
  "path": "/api/v1/auth/login",
  "timestamp": "2026-06-12T04:00:00Z"
}
```

---

### 3. Test JWT

```
GET /test
```

**Response:** Trả về chuỗi JWT token test.

---

## 🔒 Protected Endpoints (cần Bearer Token)

Tất cả endpoints khác ngoài public list đều yêu cầu:

```
Authorization: Bearer <access_token>
```

---

## 🌐 Internationalization (i18n)

### Cách chuyển ngôn ngữ

**Cách 1 — Header (khuyến nghị):**
```
Accept-Language: vi
```

**Cách 2 — Query Parameter:**
```
GET /api/v1/auth/login?lang=vi
```

### Ví dụ response tiếng Việt

```json
{
  "success": false,
  "status": 401,
  "error": "INVALID_CREDENTIALS",
  "message": "Tên đăng nhập hoặc mật khẩu không đúng",
  "path": "/api/v1/auth/login",
  "timestamp": "2026-06-12T04:00:00Z"
}
```

---

## Danh sách Error Codes

| ErrorCode | HTTP Status | EN Message | VI Message |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | Validation failed | Xác thực dữ liệu thất bại |
| `INVALID_KEY` | 400 | Invalid message key | Khóa thông điệp không hợp lệ |
| `USER_EXISTED` | 400 | User already exists | Người dùng đã tồn tại |
| `USER_NOT_FOUND` | 404 | User not found | Không tìm thấy người dùng |
| `USERNAME_INVALID` | 400 | Username must be at least 3 characters | Tên đăng nhập phải có ít nhất 3 ký tự |
| `INVALID_PASSWORD` | 400 | Password must be at least 8 characters | Mật khẩu phải có ít nhất 8 ký tự |
| `INVALID_REQUEST_BODY` | 400 | Request body is malformed or unreadable | Nội dung yêu cầu không đúng định dạng |
| `UNAUTHORIZED` | 401 | Unauthorized access | Truy cập không được phép |
| `FORBIDDEN` | 403 | You do not have permission | Bạn không có quyền truy cập |
| `UNAUTHENTICATED` | 401 | Unauthenticated | Chưa xác thực |
| `INVALID_CREDENTIALS` | 401 | Invalid username or password | Tên đăng nhập hoặc mật khẩu không đúng |
| `EXTERNAL_SERVICE_ERROR` | 503 | External service unavailable | Dịch vụ bên ngoài không khả dụng |
| `DATABASE_ERROR` | 500 | Database connection failed | Kết nối cơ sở dữ liệu thất bại |
| `UNCATEGORIZED_EXCEPTION` | 500 | Uncategorized error | Lỗi không xác định |

---

## Validation Error Response (khi nhiều field lỗi)

```json
{
  "success": false,
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Validation failed",
  "path": "/api/v1/auth/register",
  "validationErrors": {
    "userName": "Username không được để trống",
    "password": "Password phải có ít nhất 8 ký tự"
  },
  "timestamp": "2026-06-12T04:00:00Z"
}
```
