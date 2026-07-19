package iuh.fit.authservice.presentation.controller;

import iuh.fit.authservice.application.features.devices.commands.revoke_device.RevokeDeviceCommand;
import iuh.fit.authservice.application.features.devices.commands.revoke_device.RevokeDeviceCommandHandler;
import iuh.fit.authservice.application.features.devices.queries.get_user_devices.GetUserDevicesQuery;
import iuh.fit.authservice.application.features.devices.queries.get_user_devices.GetUserDevicesQueryHandler;
import iuh.fit.authservice.application.features.devices.queries.get_user_devices.GetUserDevicesResponse;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Device Management", description = "APIs for managing user devices")
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {

    GetUserDevicesQueryHandler getUserDevicesQueryHandler;
    RevokeDeviceCommandHandler revokeDeviceCommandHandler;
    JwtUtil jwtUtil;

    @GetMapping
    @Operation(summary = "Get user devices", description = "Retrieves a list of all active devices for the authenticated user")
    public ResponseEntity<ApiResponse<GetUserDevicesResponse>> getUserDevices() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        GetUserDevicesQuery query = new GetUserDevicesQuery(UUID.fromString(userIdStr));
        GetUserDevicesResponse response = getUserDevicesQueryHandler.handle(query);

        return ResponseEntity.ok(ApiResponse.success(response, "Fetched devices successfully"));
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Revoke device", description = "Revokes access for a specific device by its ID")
    public ResponseEntity<ApiResponse<Void>> revokeDevice(@PathVariable UUID deviceId) {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        RevokeDeviceCommand command = new RevokeDeviceCommand(UUID.fromString(userIdStr), deviceId);
        revokeDeviceCommandHandler.handle(command);

        return ResponseEntity.ok(ApiResponse.success(null, "Device revoked successfully"));
    }
}
