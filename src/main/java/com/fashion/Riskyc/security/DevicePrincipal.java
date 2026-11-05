package com.fashion.Riskyc.security;

/** An authenticated till (see PosDevice). enterpriseId null = the platform's own till. */
public record DevicePrincipal(String deviceId, String deviceName, String enterpriseId) {
}
