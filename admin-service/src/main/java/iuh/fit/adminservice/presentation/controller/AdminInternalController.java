package iuh.fit.adminservice.presentation.controller;

import iuh.fit.adminservice.application.features.command.AddBlacklistWordCommand;
import iuh.fit.adminservice.application.features.query.GetSystemConfigsQuery;
import iuh.fit.adminservice.domain.entities.BlacklistedWord;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/admin")
@RequiredArgsConstructor
public class AdminInternalController {

    private final AddBlacklistWordCommand addBlacklistWordCommand;
    private final GetSystemConfigsQuery getSystemConfigsQuery;

    @PostMapping("/blacklist/learn")
    public ResponseEntity<BlacklistedWord> learnSensitiveWord(@RequestBody String word) {
        return ResponseEntity.ok(addBlacklistWordCommand.execute(word));
    }

    @GetMapping("/system-configs")
    public ResponseEntity<Map<String, String>> getInternalSystemConfigs() {
        return ResponseEntity.ok(getSystemConfigsQuery.execute());
    }
}
