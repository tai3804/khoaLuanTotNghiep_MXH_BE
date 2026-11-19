package iuh.fit.callservice.application.features.call.queries.get_call_history;

import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.callservice.application.mapper.CallFeatureMapper;
import iuh.fit.callservice.domain.entities.CallParticipant;
import iuh.fit.callservice.domain.entities.CallSession;
import iuh.fit.callservice.infrastructure.persistence.repository.CallParticipantRepository;
import iuh.fit.callservice.infrastructure.persistence.repository.CallSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetCallHistoryQueryHandler {

    CallSessionRepository callSessionRepository;
    CallParticipantRepository callParticipantRepository;
    CallFeatureMapper callFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetCallHistoryResult> handle(GetCallHistoryQuery query) {
        BaseFilter filter = query.getFilter() != null ? query.getFilter() : new BaseFilter();
        int pageZeroBased = Math.max(0, filter.getPage() - 1);
        int size = filter.getSize() > 0 ? filter.getSize() : 10;

        PageRequest pageRequest = PageRequest.of(pageZeroBased, size);
        Page<CallParticipant> participantPage = callParticipantRepository.findUserCallHistory(query.getCurrentUserId(), pageRequest);

        // Tải phiên gọi và danh sách người tham gia theo lô: 2 truy vấn cho cả trang thay vì 2 truy vấn cho mỗi dòng (N+1)
        List<UUID> sessionIds = participantPage.getContent().stream().map(CallParticipant::getCallSessionId).distinct().toList();
        Map<UUID, CallSession> sessions = sessionIds.isEmpty() ? Map.of()
                : callSessionRepository.findAllById(sessionIds).stream().collect(Collectors.toMap(CallSession::getId, s -> s, (x, y) -> x));
        Map<UUID, List<UUID>> participantsBySession = new HashMap<>();
        if (!sessionIds.isEmpty()) {
            for (CallParticipant cp : callParticipantRepository.findByCallSessionIdIn(sessionIds)) {
                participantsBySession.computeIfAbsent(cp.getCallSessionId(), k -> new ArrayList<>()).add(cp.getUserId());
            }
        }

        List<GetCallHistoryResult> results = new ArrayList<>();
        for (CallParticipant participant : participantPage.getContent()) {
            CallSession session = sessions.get(participant.getCallSessionId());
            if (session != null) {
                GetCallHistoryResult result = callFeatureMapper.toGetCallHistoryResult(session, participant);
                result.setMyRole(participant.getRole());
                result.setMyStatus(participant.getStatus());
                result.setParticipantUserIds(participantsBySession.getOrDefault(session.getId(), List.of()));
                results.add(result);
            }
        }

        return PagedResponse.<GetCallHistoryResult>builder()
                .content(results)
                .page(participantPage.getNumber())
                .size(participantPage.getSize())
                .totalElements(participantPage.getTotalElements())
                .totalPages(participantPage.getTotalPages())
                .last(participantPage.isLast())
                .build();
    }
}
