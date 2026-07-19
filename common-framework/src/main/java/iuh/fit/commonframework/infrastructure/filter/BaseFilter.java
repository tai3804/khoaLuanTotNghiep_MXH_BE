package iuh.fit.commonframework.infrastructure.filter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BaseFilter {
    String keyword;
    
    @Min(value = 1, message = "Page must be >= 1")
    @Builder.Default
    int page = 1;
    
    @Min(value = 1, message = "Size must be >= 1")
    @Max(value = 100, message = "Size maximum is 100")
    @Builder.Default
    int size = 10;
    
    @Pattern(regexp = "^[a-zA-Z0-9_.]*$", message = "SortBy contains invalid characters")
    String sortBy;
    
    @Builder.Default
    SortDirection sortDirection = SortDirection.ASC;
    
    @Size(max = 20, message = "Maximum of 20 filters allowed")
    @Builder.Default
    Map<@Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "Filter key contains invalid characters") String, Object> filters = new HashMap<>();
}
