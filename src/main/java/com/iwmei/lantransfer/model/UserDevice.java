package com.iwmei.lantransfer.model;

// 用户设备数据对象
public record UserDevice(String id, String nickname, String deviceName, DeviceStatus status, String lastSeen,
                         String avatarText, String color, boolean imageAvatar) {
}
