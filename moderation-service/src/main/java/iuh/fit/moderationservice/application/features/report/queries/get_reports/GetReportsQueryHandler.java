package iuh.fit.moderationservice.application.features.report.queries.get_reports;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.moderationservice.application.mapper.ReportFeatureMapper;
import iuh.fit.moderationservice.domain.entities.Report;
import iuh.fit.moderationservice.domain.enums.ReportStatus;
import iuh.fit.moderationservice.infrastructure.persistence.repository.ReportRepository;
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
public class GetReportsQueryHandler {

    ReportRepository reportRepository;
    ReportFeatureMapper reportFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetReportsResult> handle(GetReportsQuery query) {
        BaseFilter filter = query.getFilter() != null ? query.getFilter() : new BaseFilter();
        int pageZeroBased = Math.max(0, filter.getPage() - 1);
        int size = filter.getSize() > 0 ? filter.getSize() : 10;

        Sort.Direction direction = (filter.getSortDirection() != null && filter.getSortDirection().name().equalsIgnoreCase("DESC"))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortBy = (filter.getSortBy() != null && !filter.getSortBy().isBlank()) ? filter.getSortBy() : "createdAt";

        PageRequest pageRequest = PageRequest.of(pageZeroBased, size, Sort.by(direction, sortBy));

        Page<Report> reportPage;
        if (filter.getFilters() != null && filter.getFilters().containsKey("status")) {
            try {
                ReportStatus status = ReportStatus.valueOf(filter.getFilters().get("status").toString().toUpperCase());
                reportPage = reportRepository.findByStatus(status, pageRequest);
            } catch (Exception e) {
                reportPage = reportRepository.findAll(pageRequest);
            }
        } else {
            reportPage = reportRepository.findAll(pageRequest);
        }

        List<GetReportsResult> results = reportPage.getContent().stream()
                .map(reportFeatureMapper::toGetReportsResult)
                .toList();

        return PagedResponse.<GetReportsResult>builder()
                .content(results)
                .page(reportPage.getNumber())
                .size(reportPage.getSize())
                .totalElements(reportPage.getTotalElements())
                .totalPages(reportPage.getTotalPages())
                .last(reportPage.isLast())
                .build();
    }
}
