package iuh.fit.userservice.application.features.user_connection.queries.get_connections;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.userservice.application.mapper.UserConnectionApplicationMapper;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetConnectionsQueryHandler {

    UserConnectionRepository userConnectionRepository;
    UserConnectionApplicationMapper userConnectionApplicationMapper;

    public PagedResponse<UserConnectionResult> handle(GetConnectionsQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), Sort.by("createdAt").descending());
        Page<UserConnection> pageResult;

        String mode = query.getMode() != null ? query.getMode().toUpperCase() : "FRIENDS";

        switch (mode) {
            case "FOLLOWERS":
                pageResult = userConnectionRepository.findFollowersOfUser(query.getUserId(), pageable);
                break;
            case "FOLLOWING":
                pageResult = userConnectionRepository.findFollowingOfUser(query.getUserId(), pageable);
                break;
            case "PENDING_REQUESTS":
                pageResult = userConnectionRepository.findPendingFriendRequestsForUser(query.getUserId(), pageable);
                break;
            case "FRIENDS":
            default:
                pageResult = userConnectionRepository.findFriendsOfUser(query.getUserId(), pageable);
                break;
        }

        return userConnectionApplicationMapper.toPagedResponse(pageResult);
    }
}
