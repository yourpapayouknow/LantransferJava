package com.iwmei.lantransfer.model;

import java.time.LocalDateTime;

// 用户资料数据对象
public record Profile(String nickname, String userId, String deviceName, String signature, LocalDateTime registeredAt,
                      LocalDateTime lastLoginAt, String version, String language, UserStatus status, String avatar) {
    // 使用默认状态构造用户资料
    public Profile(String nickname, String userId, String deviceName, String signature, LocalDateTime registeredAt,
                   LocalDateTime lastLoginAt, String version, String language) {
        this(nickname, userId, deviceName, signature, registeredAt, lastLoginAt, version, language, UserStatus.DEFAULT, "");
    }

    // 使用指定状态构造用户资料
    public Profile(String nickname, String userId, String deviceName, String signature, LocalDateTime registeredAt,
                   LocalDateTime lastLoginAt, String version, String language, UserStatus status) {
        this(nickname, userId, deviceName, signature, registeredAt, lastLoginAt, version, language, status, "");
    }
}
