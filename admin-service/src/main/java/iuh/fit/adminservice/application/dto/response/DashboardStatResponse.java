package iuh.fit.adminservice.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatResponse {
    private long totalUsers;
    private long totalPosts;
    private long totalReports;
    private long pendingReports;
    private long newUsersToday;
    private long newPostsToday;
    private long resolvedReportsToday;
    private long activeUsersNow;
}
