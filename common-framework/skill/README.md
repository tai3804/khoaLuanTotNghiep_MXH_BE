# Tóm tắt Hệ Thống & Quy Chuẩn Code (Dành cho AI Agent)

Đây là file chứa toàn bộ **ngữ cảnh hiện tại (Context)** và **quy tắc lập trình (Rules)** của dự án `common-framework`. Đọc kỹ file này trước khi thực hiện bất kỳ thay đổi nào để đảm bảo tính đồng nhất.

## 1. System Context (Hệ thống hiện đang có gì?)
Dự án này là **Common Framework** dành cho các Microservices (Spring Boot 3, Java 21). Chứa các thành phần dùng chung:
- **Security (Bảo mật)**: Đã cấu hình làm **Resource Server** hoàn chỉnh, xác thực JWT bằng chuẩn **bất đối xứng RS256**. Lấy Public Key từ properties `app.security.jwt.public-key`.
- **Caching**: Đã cấu hình **Redis Cache** (không dùng Caffeine). Dữ liệu cache được serialize chuẩn JSON để dễ đọc. Cấu hình TTL được tiêm từ `spring.cache.redis.time-to-live`.
- **Base Components (Kiến trúc lõi)**:
  - `BaseEntity`: Class cha cho mọi Entity (có sẵn UUID, Audit fields).
  - `BaseMapper`: Interface dùng chung cho **MapStruct** để map qua lại giữa Entity và DTO, hỗ trợ `updateEntityFromDto` bỏ qua null properties.
  - **MapStruct cho DTO <-> Entity**:
    - Dữ liệu đi vào Controller (Command/Query) hoặc trả ra (Response) BẮT BUỘC phải map qua Entity (nếu cần thiết) bằng **MapStruct**, TUYỆT ĐỐI KHÔNG dùng hàm `Builder()` thủ công (như `User.builder().email(dto.getEmail()).build()`).
    - Các giá trị mặc định của Domain (như `status = ACTIVE`) thì khai báo trực tiếp trong MapStruct thông qua `@Mapping(target = "status", expression = "java(...)")`.
  - **Validation**: Đặt validation annotations (`@NotNull`, `@NotBlank`) trực tiếp trên DTO (Command/Query). Sẽ được tự động bắt bởi GlobalExceptionHandler.
  - `ApiResponse` & `GlobalExceptionHandler`: Chuẩn hóa JSON Response cho mọi API và tự động bắt lỗi từ hệ thống.

## 2. Coding Conventions (Phải làm gì khi code?)
1. **Always import classes at the top of the file:** KHÔNG bao giờ dùng tên class đầy đủ (fully qualified name) trực tiếp trong code. Hãy import ở đầu file.
2. **Use @FieldDefaults:** Luôn dùng `@FieldDefaults(level = AccessLevel.PRIVATE)` của Lombok ở class level thay cho từ khóa `private` ở từng biến.
3. **Comment conventions:** Chỉ viết chú thích (bằng tiếng Việt) cho các hàm (methods). KHÔNG viết chú thích cho từng field lẻ tẻ.
4. **Comment style:** Lời chú thích phải **cực kỳ ngắn gọn và mang tính kỹ thuật cao**. KHÔNG dùng văn phong kể lể, mô tả dài dòng (VD: Dùng "Tạo success response." thay vì "Hàm này giúp tạo một phản hồi thành công...").
5. **Validation:** Luôn sử dụng thư viện `jakarta.validation.constraints.*` (không dùng javax) cho các input từ client. Bảo vệ các thuộc tính Map linh hoạt bằng `@Pattern` ngay trên Type Parameter để chống lỗ hổng Injection.
6. **Property Configurations:** Tránh cấu hình các custom properties dưới namespace mặc định của Spring (VD: tránh dùng `spring.security.jwt...` mà hãy dùng custom như `app.security.jwt...`) để IDE không báo cảnh báo "Unknown Property", ngoại trừ các propery gốc của Spring (như `spring.cache...`).
7. **Không sử dụng Nested Class:** KHÔNG code nhiều class chung 1 file (ví dụ: không tạo `static class UserDto` bên trong `LoginUserResponse`). Bắt buộc tách ra các file `.java` riêng biệt.
8. **Quy tắc đặt tên Class DTO:** KHÔNG sử dụng hậu tố `Dto` (ví dụ `UserDto`). Phải dùng đúng loại thông điệp: `Command`, `Query`, `Request`, hoặc `Response` (ví dụ: `UserResponse`).

## 3. Architecture Rules (Quy tắc Kiến trúc Hệ thống)
Dự án áp dụng **Clean Architecture** kết hợp **CQRS** theo mô hình **Vertical Slicing** (chia theo tính năng). Mọi module/service mới BẮT BUỘC tuân thủ cấu trúc thư mục sau:

```text
├── presentation                        (LAYER 4: GIAO TIẾP VỚI CLIENT)
│   ├── controller                      (Chỉ nhận request, đẩy qua Mediator/Bus)
│   └── client                          (Gọi các api bên ngoài)
│
├── application                         (LAYER 2: LOGIC ỨNG DỤNG & CQRS)
│   ├── common                          (Các cấu hình chung của tầng Application)
│   │   ├── behavior                    (Validation, Logging Pipeline)
│   │   └── exceptions                  (Custom Exception cho Application)
│   │
│   └── features                        (Chia theo tính năng/nghiệp vụ cụ thể)
│       └── [feature_name]              (Ví dụ: users, auth, devices)
│           ├── commands                (Luồng Ghi - Thay đổi trạng thái hệ thống)
│           │   └── [command_name]      (Ví dụ: create_user)
│           │       ├── [Name]Command.java        (DTO đầu vào)
│           │       ├── [Name]CommandHandler.java (Xử lý logic)
│           │       └── [Name]Response.java       (DTO đầu ra)
│           └── queries                 (Luồng Đọc - Lấy dữ liệu, không ghi đè)
│               └── [query_name]
│                   ├── [Name]Query.java
│                   ├── [Name]QueryHandler.java
│                   └── [Name]Response.java
│
├── domain                              (LAYER 1: NGHIỆP VỤ CỐT LÕI - CORE)
│   ├── entities                        (Các thực thể thuần túy, chứa luật nghiệp vụ, UUID v7)
│   ├── exceptions                      (Các ngoại lệ liên quan đến luật nghiệp vụ)
│   └── repository                      (Chỉ chứa INTERFACE - Bản thiết kế, không extends JpaRepository)
│
└── infrastructure                      (LAYER 3: CÔNG NGHỆ & CÀI ĐẶT CHI TIẾT)
    ├── persistence                     (Kết nối Cơ sở dữ liệu)
    │   ├── jpa                         (Chứa các JpaRepository của Spring)
    │   ├── models                      (DATABASE ENTITY / ORM MODEL, chứa @Entity)
    │   ├── mapper                      (Chuyển đổi giữa Domain Entity và DbModel)
    │   └── repository_impl             (Cài đặt thực tế của các Interface từ tầng Domain)
    │
    ├── identity                        (Bảo mật: Spring Security, JWT, OAuth2)
    └── shared                          (Các dịch vụ bên ngoài khác như email, filter)
```

## 4. Các thành phần tái sử dụng trong `common-framework` (MUST REUSE)
Khi code các service con (ví dụ `auth-service`), **TUYỆT ĐỐI KHÔNG CODE LẠI** các thành phần sau mà phải import từ `common-framework`:

### 4.1. Application Layer (DTO & Exception)
- `iuh.fit.commonframework.application.dto.ApiResponse<T>`:
  - Class bọc response trả về chuẩn (`code`, `message`, `data`).
  - Dùng hàm static: `ApiResponse.success(data)`, `ApiResponse.success(data, message)`, `ApiResponse.error(code, message)`.
- `iuh.fit.commonframework.application.exception.BusinessException`:
  - Throw exception nghiệp vụ: `throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid data")`.
- `iuh.fit.commonframework.application.exception.ErrorCode`: Enum chứa mã lỗi chuẩn.
- `iuh.fit.commonframework.application.exception.GlobalExceptionHandler`: Tự động bắt mọi `Exception`, `BusinessException`, `MethodArgumentNotValidException` và trả về `ApiResponse` lỗi. KHÔNG CẦN `@ExceptionHandler` trong controller.

### 4.2. Mapper & Entity Base
- `iuh.fit.commonframework.application.mapper.BaseMapper<E, D>`:
  - Interface cho MapStruct. Cung cấp: `toEntity(dto)`, `toDto(entity)`, `updateEntityFromDto(dto, entity)` (tự bỏ qua null).
  - Cách dùng: `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE) public interface MyMapper extends BaseMapper<DbModel, DomainEntity> {}`
- `iuh.fit.commonframework.domain.entity.BaseEntity`:
  - Dành CHỈ cho DbModel ở tầng Infrastructure (không dùng cho pure domain entity).
  - Tự sinh `id` bằng UUIDv7 (sắp xếp được theo thời gian).
  - Tự cập nhật `createdAt`, `updatedAt`, `deleted` nhờ `@EntityListeners(AuditingEntityListener.class)`.

### 4.3. Infrastructure Layer (Filter, Security, Cache)
- `iuh.fit.commonframework.infrastructure.filter.BaseFilter`:
  - Class kế thừa cho mọi Filter DTO (phân trang, search).
  - Cung cấp sẵn: `page`, `size`, `sortBy`, `sortDirection`.
- `iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository<T, ID>`:
  - Interface Repository cha, kế thừa `JpaRepository` và `JpaSpecificationExecutor`. Luôn cho các JpaRepository của service con `extends BaseJpaRepository`.
- `iuh.fit.commonframework.infrastructure.security.JwtUtil` & `SecurityConfig`:
  - Tự động bắt JWT, kiểm tra chữ ký public key, cấp quyền truy cập. Service con không cần cấu hình thêm Security, chỉ cần chặn Role ở controller bằng `@PreAuthorize("hasRole('ADMIN')")`.
- `iuh.fit.commonframework.infrastructure.cache.CacheConfig`: Tự động kích hoạt Redis Cache. Dùng `@Cacheable`, `@CachePut`, `@CacheEvict` ở Service.
