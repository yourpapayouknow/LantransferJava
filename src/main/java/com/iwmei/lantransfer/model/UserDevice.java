package com.iwmei.lantransfer.model;
public record UserDevice(String id, String nickname, String deviceName, DeviceStatus status, String lastSeen,
                         String avatarText, String color, boolean imageAvatar, String host, int port,
                         UserStatus userStatus, String signature, String avatar) {
    private static final String GROUP_PREFIX = "GROUP:";
    public UserDevice(String id, String nickname, String deviceName, DeviceStatus status, String lastSeen,
                      String avatarText, String color, boolean imageAvatar) {
        this(id, nickname, deviceName, status, lastSeen, avatarText, color, imageAvatar, "", 0);
    }
    public UserDevice(String id, String nickname, String deviceName, DeviceStatus status, String lastSeen,
                      String avatarText, String color, boolean imageAvatar, String host, int port) {
        this(id, nickname, deviceName, status, lastSeen, avatarText, color, imageAvatar, host, port, UserStatus.DEFAULT);
    }
    public UserDevice(String id, String nickname, String deviceName, DeviceStatus status, String lastSeen,
                      String avatarText, String color, boolean imageAvatar, String host, int port,
                      UserStatus userStatus) {
        this(id, nickname, deviceName, status, lastSeen, avatarText, color, imageAvatar, host, port, userStatus, "", "");
    }
    public boolean reachable() {
        return host != null && !host.isBlank() && port > 0;
    }
    public boolean groupTarget() {
        return id != null && id.startsWith(GROUP_PREFIX);
    }
    public String groupName() {
        return groupTarget() ? id.substring(GROUP_PREFIX.length()) : "";
    }
    public static UserDevice group(String name, int members) {
        return group(name, members, "");
    }
    public static UserDevice group(String name, int members, String code) {
        String groupName = cleanGroupName(name);
        return new UserDevice(GROUP_PREFIX + groupName, "组：" + groupName, members + " 个成员",
                DeviceStatus.ONLINE, "本地分组", "组", "#7a52d8", false, "", 0, UserStatus.DEFAULT,
                code == null ? "" : code.trim(), "");
    }
    public static String cleanGroupName(String name) {
        String value = name == null ? "" : name.trim();
        return value.isBlank() ? "默认分组" : value;
    }
}
