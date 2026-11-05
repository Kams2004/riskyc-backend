package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DeviceHeartbeatRequest(@Size(max = 40) String appVersion, @PositiveOrZero Integer pendingSales) {
}
