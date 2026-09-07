package iuh.fit.moderationservice.application.features.moderation.queries.get_moderation_logs;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.moderationservice.application.mapper.ModerationFeatureMapper;
import iuh.fit.moderationservice.domain.entities.ModerationLog;
import iuh.fit.moderationservice.infrastructure.persistence.repository.ModerationLogRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetModerationLogsQueryHandler {

    ModerationLogRepository moderationLogRepository;
    ModerationFeatureMapper moderationFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetModerationLogsResult> handle(GetModerationLogsQuery query) {
        BaseFilter filter = query.getFilter() != null ? query.getFilter() : new BaseFilter();
        int pageZeroBased = Math.max(0, filter.getPage() - 1);
        int size = filter.getSize() > 0 ? filter.getSize() : 10;

        Sort.Direction direction = (filter.getSortDirection() != null && filter.getSortDirection().name().equalsIgnoreCase("DESC"))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortBy = (filter.getSortBy() != null && !filter.getSortBy().isBlank()) ? filter.getSortBy() : "createdAt";

        PageRequest pageRequest = PageRequest.of(pageZeroBased, size, Sort.by(direction, sortBy));

        Page<ModerationLog> logPage = moderationLogRepository.findAll(pageRequest);

        List<GetModerationLogsResult> results = logPage.getContent().stream()
                .map(moderationFeatureMapper::toGetModerationLogsResult)
                .toList();

        return PagedResponse.<GetModerationLogsResult>builder()
                .content(results)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .last(logPage.isLast())
                .build();
    }
}
