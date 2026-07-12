package com.iwmei.lantransfer.model;
import java.time.LocalDateTime;
public record Profile(String nickname, String userId, String deviceName, String signature, LocalDateTime registeredAt,
                      LocalDateTime lastLoginAt, String version, String language, UserStatus status, String avatar) {
    public Profile(String nickname, String userId, String deviceName, String signature, LocalDateTime registeredAt,
                   LocalDateTime lastLoginAt, String version, String language) {
        this(nickname, userId, deviceName, signature, registeredAt, lastLoginAt, version, language, UserStatus.DEFAULT, "");
    }
    public Profile(String nickname, String userId, String deviceName, String signature, LocalDateTime registeredAt,
                   LocalDateTime lastLoginAt, String version, String language, UserStatus status) {
        this(nickname, userId, deviceName, signature, registeredAt, lastLoginAt, version, language, status, "");
    }
}
