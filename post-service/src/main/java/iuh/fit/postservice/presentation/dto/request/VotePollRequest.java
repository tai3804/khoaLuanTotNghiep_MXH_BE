package iuh.fit.postservice.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VotePollRequest {
    @NotEmpty(message = "Danh sách lựa chọn không được để trống")
    private List<String> optionIds;
}
