package com.iwmei.lantransfer.model;

import java.time.LocalDateTime;

public record Profile(String nickname, String userId, String deviceName, String signature, LocalDateTime registeredAt,
                      LocalDateTime lastLoginAt, String version, String language) {
}
