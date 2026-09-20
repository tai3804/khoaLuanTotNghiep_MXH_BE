package iuh.fit.adminservice.application.features.query;

import iuh.fit.adminservice.application.dto.response.ReportResponse;
import iuh.fit.adminservice.application.mapper.ReportMapper;
import iuh.fit.adminservice.domain.enums.ReportStatus;
import iuh.fit.adminservice.domain.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetReportsQuery {
    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;

    public List<ReportResponse> execute() {
        return reportMapper.toDtoList(reportRepository.findAll());
    }
    
    public List<ReportResponse> executePending() {
        return reportMapper.toDtoList(reportRepository.findByStatus(ReportStatus.PENDING));
    }
}

