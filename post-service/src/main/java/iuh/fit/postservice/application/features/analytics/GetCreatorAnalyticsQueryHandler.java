package iuh.fit.postservice.application.features.analytics;

import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.presentation.dto.response.analytics.DailyMetricDto;
import iuh.fit.postservice.presentation.dto.response.analytics.PostAnalyticsResponse;
import iuh.fit.postservice.presentation.dto.response.analytics.TopPostMetricDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetCreatorAnalyticsQueryHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;

    @Transactional(readOnly = true)
    public PostAnalyticsResponse handle(UUID authorId, String period) {
        int days = 28;
        if ("7d".equalsIgnoreCase(period)) {
            days = 7;
        } else if ("60d".equalsIgnoreCase(period)) {
            days = 60;
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 1. Initialize map for every day in the period
        Map<String, DailyMetricDto> dailyMap = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            String dateStr = startDate.plusDays(i).format(formatter);
            dailyMap.put(dateStr, DailyMetricDto.builder()
                    .date(dateStr)
                    .views(0)
                    .likes(0)
                    .comments(0)
                    .shares(0)
                    .engagements(0)
                    .build());
        }

        // 2. Fetch author's posts
        List<Post> allPosts = postRepository.findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(authorId);

        long totalViews = 0;
        long totalLikes = 0;
        long totalComments = 0;
        long totalShares = 0;

        for (Post post : allPosts) {
            long pViews = post.getViewCount();
            long pLikes = post.getLikeCount();
            long pComments = post.getCommentCount();
            long pShares = post.getShareCount();
            long pEngagements = pLikes + pComments + pShares;

            totalViews += pViews;
            totalLikes += pLikes;
            totalComments += pComments;
            totalShares += pShares;

            if (post.getCreatedAt() != null) {
                LocalDate postDate = post.getCreatedAt().toLocalDate();
                if (!postDate.isBefore(startDate) && !postDate.isAfter(endDate)) {
                    String dKey = postDate.format(formatter);
                    DailyMetricDto metric = dailyMap.get(dKey);
                    if (metric != null) {
                        metric.setViews(metric.getViews() + pViews);
                        metric.setLikes(metric.getLikes() + pLikes);
                        metric.setComments(metric.getComments() + pComments);
                        metric.setShares(metric.getShares() + pShares);
                        metric.setEngagements(metric.getEngagements() + pEngagements);
                    }
                }
            }
        }

        long totalEngagements = totalLikes + totalComments + totalShares;
        long totalReach = (long) Math.ceil(totalViews * 0.88);
        double avgEngagementRate = totalViews > 0
                ? Math.round(((double) totalEngagements / totalViews) * 10000.0) / 100.0
                : 0.0;

        // 3. Top performing posts (by views and interactions)
        List<TopPostMetricDto> topPosts = allPosts.stream()
                .sorted((a, b) -> Long.compare(b.getViewCount(), a.getViewCount()))
                .limit(8)
                .map(post -> {
                    List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(post.getId());
                    String firstUrl = (mediaList != null && !mediaList.isEmpty()) ? mediaList.get(0).getFileUrl() : null;

                    long pEngagements = post.getLikeCount() + post.getCommentCount() + post.getShareCount();
                    double postRate = post.getViewCount() > 0
                            ? Math.round(((double) pEngagements / post.getViewCount()) * 10000.0) / 100.0
                            : 0.0;

                    return TopPostMetricDto.builder()
                            .id(post.getId())
                            .content(post.getContent())
                            .firstMediaUrl(firstUrl)
                            .createdAt(post.getCreatedAt())
                            .viewCount(post.getViewCount())
                            .likeCount(post.getLikeCount())
                            .commentCount(post.getCommentCount())
                            .shareCount(post.getShareCount())
                            .engagementRate(postRate)
                            .build();
                })
                .toList();

        return PostAnalyticsResponse.builder()
                .totalViews(totalViews)
                .totalReach(totalReach)
                .totalEngagements(totalEngagements)
                .totalPosts(allPosts.size())
                .totalLikes(totalLikes)
                .totalComments(totalComments)
                .totalShares(totalShares)
                .avgEngagementRate(avgEngagementRate)
                .dailyMetrics(new ArrayList<>(dailyMap.values()))
                .topPosts(topPosts)
                .period(days + "d")
                .build();
    }
}
