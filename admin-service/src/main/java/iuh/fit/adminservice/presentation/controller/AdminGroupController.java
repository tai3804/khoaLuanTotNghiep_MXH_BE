package iuh.fit.adminservice.presentation.controller;

import iuh.fit.adminservice.application.service.AuditLogService;
import iuh.fit.adminservice.infrastructure.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/groups")
@RequiredArgsConstructor
public class AdminGroupController {

    private final UserFeignClient userFeignClient;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<Object>> getAllGroups() {
        try {
            return ResponseEntity.ok(userFeignClient.getAllGroups());
        } catch(Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String groupId) {
        userFeignClient.deleteGroup(groupId);
        auditLogService.log("DELETE_GROUP", "GROUP", groupId, "Giải tán hội nhóm vi phạm");
        return ResponseEntity.ok().build();
    }
}
