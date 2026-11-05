package com.fashion.Riskyc.dto.response;

import java.util.UUID;

/** The device token is returned only here, once — the app must store it (encrypted) and send it as X-Device-Token. */
public record EnrollDeviceResponse(UUID deviceId, String deviceToken, UUID enterpriseId, String storeName) {
}
