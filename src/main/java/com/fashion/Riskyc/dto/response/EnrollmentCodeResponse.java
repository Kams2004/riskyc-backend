package com.fashion.Riskyc.dto.response;

import java.time.Instant;

public record EnrollmentCodeResponse(String code, Instant expiresAt) {
}
