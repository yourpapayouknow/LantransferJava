package com.iwmei.lantransfer.model;

public record UserDevice(String id, String nickname, String deviceName, DeviceStatus status, String lastSeen,
                         String avatarText, String color, boolean imageAvatar) {
}
