package com.iwmei.lantransfer.model;

import java.time.LocalDateTime;

// 用户资料数据对象
public record Profile(String nickname, String userId, String deviceName, String signature, LocalDateTime registeredAt,
                      LocalDateTime lastLoginAt, String version, String language) {
}
