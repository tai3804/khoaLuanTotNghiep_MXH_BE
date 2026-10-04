package iuh.fit.adminservice.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String userId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String fullName;
    private String email;
    private String username;
    private String avatarUrl;
    private String coverUrl;
    private String bio;
    private String gender;
    private String phoneNumber;
    private String dateOfBirth;
    private String location;
    private String website;
    private Boolean isOnline;
    private String lastActiveAt;
    private String role;
    private String status; // ACTIVE, BANNED
    private Long followerCount;
    private Long followingCount;
    private Long friendCount;
    private String createdAt;
    private String updatedAt;
}
