package com.fashion.Riskyc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnrollDeviceRequest(
        @NotBlank String code,
        @NotBlank @Size(max = 80) String name,
        @Size(max = 20) String platform,
        @Size(max = 40) String appVersion
) {
}
