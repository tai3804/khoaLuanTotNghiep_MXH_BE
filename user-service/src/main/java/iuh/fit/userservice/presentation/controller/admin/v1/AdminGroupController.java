package iuh.fit.userservice.presentation.controller.admin.v1;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.userservice.application.exception.UserServiceErrorCode;
import iuh.fit.userservice.domain.entities.Group;
import iuh.fit.userservice.domain.repository.GroupRepository;
import iuh.fit.userservice.infrastructure.kafka.producers.GroupEventPublisher;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/groups/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminGroupController {

    GroupRepository groupRepository;
    GroupEventPublisher groupEventPublisher;

    @GetMapping("/all")
    public ResponseEntity<List<Group>> getAllGroups() {
        return ResponseEntity.ok(groupRepository.findAll());
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID groupId) {
        log.info("Admin requested delete for group: {}", groupId);
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(UserServiceErrorCode.GROUP_NOT_FOUND));

        group.setDeleted(true);
        groupRepository.save(group);
        groupEventPublisher.publishGroupDeleted(groupId);
        return ResponseEntity.noContent().build();
    }
}
