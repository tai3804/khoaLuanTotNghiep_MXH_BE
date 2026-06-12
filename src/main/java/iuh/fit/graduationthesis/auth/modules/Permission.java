package iuh.fit.graduationthesis.auth.modules;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Entity
@Table(name = "permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @NotBlank(message = "{validation.permission.name.not_blank}")
    @Column(unique = true, nullable = false, length = 100)
    String name; // Ví dụ: "READ_PRIVILEGES", "BOOKING_CREATE"

    @Column(length = 255)
    String description; // Mô tả tiếng Việt để hiển thị lên giao diện Admin
}