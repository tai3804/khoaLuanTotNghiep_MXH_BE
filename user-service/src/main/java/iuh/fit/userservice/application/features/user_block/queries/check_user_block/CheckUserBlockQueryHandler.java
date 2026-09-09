package iuh.fit.userservice.application.features.user_block.queries.check_user_block;

import iuh.fit.userservice.domain.repository.UserBlockRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckUserBlockQueryHandler {

    UserBlockRepository userBlockRepository;

    @Transactional(readOnly = true)
    public CheckUserBlockQueryResult handle(CheckUserBlockQuery query) {
        boolean isBlockedByMe = userBlockRepository.existsByBlockerIdAndBlockedId(query.getUserId(), query.getTargetId());
        boolean isBlockedByTarget = userBlockRepository.existsByBlockerIdAndBlockedId(query.getTargetId(), query.getUserId());

        return CheckUserBlockQueryResult.builder()
                .userId(query.getUserId())
                .targetId(query.getTargetId())
                .isBlockedByMe(isBlockedByMe)
                .isBlockedByTarget(isBlockedByTarget)
                .isBlocked(isBlockedByMe || isBlockedByTarget)
                .build();
    }
}
