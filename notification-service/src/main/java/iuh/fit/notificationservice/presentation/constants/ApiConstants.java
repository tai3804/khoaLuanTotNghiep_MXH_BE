package iuh.fit.notificationservice.presentation.constants;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class ApiConstants {

    public static final String API_PREFIX = "/api";
    public static final String VERSION_V1 = "/v1";
    public static final String BASE_API_V1 = API_PREFIX + VERSION_V1;

    public static final String NOTIFICATION_API = BASE_API_V1 + "/notifications";
}
