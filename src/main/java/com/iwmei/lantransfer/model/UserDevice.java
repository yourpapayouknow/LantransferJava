package com.iwmei.lantransfer.model;

// 用户设备数据对象
public record UserDevice(String id, String nickname, String deviceName, DeviceStatus status, String lastSeen,
                         String avatarText, String color, boolean imageAvatar, String host, int port,
                         UserStatus userStatus) {
    // 使用无网络地址的旧字段构造用户设备
    public UserDevice(String id, String nickname, String deviceName, DeviceStatus status, String lastSeen,
                      String avatarText, String color, boolean imageAvatar) {
        this(id, nickname, deviceName, status, lastSeen, avatarText, color, imageAvatar, "", 0);
    }

    // 使用网络地址和默认用户状态构造用户设备
    public UserDevice(String id, String nickname, String deviceName, DeviceStatus status, String lastSeen,
                      String avatarText, String color, boolean imageAvatar, String host, int port) {
        this(id, nickname, deviceName, status, lastSeen, avatarText, color, imageAvatar, host, port, UserStatus.DEFAULT);
    }

    // 判断设备是否具备真实传输地址
    public boolean reachable() {
        return host != null && !host.isBlank() && port > 0;
    }
}
