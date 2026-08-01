package iuh.fit.commonframework.infrastructure.util;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

public final class UuidUtil {

    private UuidUtil() {
    }

    public static UUID generateUuidV7() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
