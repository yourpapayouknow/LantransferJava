package com.iwmei.lantransfer.model;

// 用户设备数据对象
public record UserDevice(String id, String nickname, String deviceName, DeviceStatus status, String lastSeen,
                         String avatarText, String color, boolean imageAvatar, String host, int port,
                         UserStatus userStatus) {
    private static final String GROUP_PREFIX = "GROUP:";

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

    // 判断当前对象是否是本地分组传输目标
    public boolean groupTarget() {
        return id != null && id.startsWith(GROUP_PREFIX);
    }

    // 读取分组目标对应的分组名
    public String groupName() {
        return groupTarget() ? id.substring(GROUP_PREFIX.length()) : "";
    }

    // 构造本地分组传输目标
    public static UserDevice group(String name, int members) {
        String groupName = cleanGroupName(name);
        return new UserDevice(GROUP_PREFIX + groupName, "组：" + groupName, members + " 个成员",
                DeviceStatus.ONLINE, "本地分组", "组", "#7a52d8", false, "", 0, UserStatus.DEFAULT);
    }

    // 清洗分组名称，空值回退到默认分组
    public static String cleanGroupName(String name) {
        String value = name == null ? "" : name.trim();
        return value.isBlank() ? "默认分组" : value;
    }
}
