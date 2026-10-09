package iuh.fit.userservice.presentation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AudienceDemographicsResponse {
    long totalAudience;
    GenderBreakdownDto genderBreakdown;
    List<AgeRangeMetricDto> ageRanges;
    List<CityMetricDto> topCities;
    List<HourlyMetricDto> hourlyActivity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class GenderBreakdownDto {
        double malePercentage;
        double femalePercentage;
        double otherPercentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AgeRangeMetricDto {
        String range;
        double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class CityMetricDto {
        String city;
        double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class HourlyMetricDto {
        String hour;
        int activePercentage;
    }
}
