package com.iwmei.lantransfer.service;
import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.UserDevice;
import com.iwmei.lantransfer.model.UserStatus;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// 近期传输对象仓库，负责把最近传输目标持久化到本地properties文件
final class RecentStore {
    private static final int MAX_ITEMS = 12;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final Path store;

    // 使用默认用户目录近期对象文件初始化仓库
    RecentStore() {
        this(AppFiles.dataDir().resolve("recent.properties"));
    }

    // 使用指定近期对象文件初始化仓库
    RecentStore(Path store) {
        this.store = store;
    }

    // 加载近期传输对象
    synchronized List<UserDevice> load() {
        if (!Files.exists(store)) {
            return List.of();
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(store, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException ignored) {
            return List.of();
        }
        List<UserDevice> devices = new ArrayList<>();
        int count = intValue(props.getProperty("count"), 0);
        for (int i = 0; i < count; i++) {
            UserDevice device = device(props, i);
            if (device != null) {
                devices.add(device);
            }
        }
        return devices;
    }

    // 记录近期传输对象
    synchronized void remember(List<UserDevice> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        Map<String, UserDevice> merged = new LinkedHashMap<>();
        for (UserDevice target : targets) {
            if (target != null && !target.id().isBlank()) {
                merged.put(target.id(), touched(target));
            }
        }
        for (UserDevice old : load()) {
            merged.putIfAbsent(old.id(), old);
        }
        save(merged.values().stream().limit(MAX_ITEMS).toList());
    }

    // 保存近期传输对象
    private void save(List<UserDevice> devices) {
        Properties props = new Properties();
        props.setProperty("repo.origin", AppFiles.repoOrigin());
        props.setProperty("count", String.valueOf(devices.size()));
        for (int i = 0; i < devices.size(); i++) {
            put(props, i, devices.get(i));
        }
        try {
            Files.createDirectories(store.getParent());
            try (Writer writer = Files.newBufferedWriter(store, StandardCharsets.UTF_8)) {
                props.store(writer, "Lantransfer recent devices");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("保存近期传输对象失败：" + store, ex);
        }
    }

    // 保存单个设备字段
    private void put(Properties props, int index, UserDevice device) {
        String prefix = index + ".";
        props.setProperty(prefix + "id", device.id());
        props.setProperty(prefix + "nickname", device.nickname());
        props.setProperty(prefix + "deviceName", device.deviceName());
        props.setProperty(prefix + "status", device.status().name());
        props.setProperty(prefix + "lastSeen", device.lastSeen());
        props.setProperty(prefix + "avatarText", device.avatarText());
        props.setProperty(prefix + "color", device.color());
        props.setProperty(prefix + "imageAvatar", String.valueOf(device.imageAvatar()));
        props.setProperty(prefix + "host", device.host());
        props.setProperty(prefix + "port", String.valueOf(device.port()));
        props.setProperty(prefix + "userStatus", userStatus(device.userStatus()).name());
        props.setProperty(prefix + "signature", device.signature());
        props.setProperty(prefix + "avatar", device.avatar());
    }

    // 读取单个设备字段
    private UserDevice device(Properties props, int index) {
        String prefix = index + ".";
        String id = props.getProperty(prefix + "id", "").trim();
        if (id.isBlank()) {
            return null;
        }
        return new UserDevice(id,
                props.getProperty(prefix + "nickname", "用户"),
                props.getProperty(prefix + "deviceName", "LOCAL-PC"),
                status(props.getProperty(prefix + "status")),
                props.getProperty(prefix + "lastSeen", ""),
                props.getProperty(prefix + "avatarText", "?"),
                props.getProperty(prefix + "color", "#4f7bd8"),
                Boolean.parseBoolean(props.getProperty(prefix + "imageAvatar", "false")),
                props.getProperty(prefix + "host", ""),
                intValue(props.getProperty(prefix + "port"), 0),
                userStatus(props.getProperty(prefix + "userStatus")),
                props.getProperty(prefix + "signature", ""),
                props.getProperty(prefix + "avatar", ""));
    }

    // 更新时间文本
    private UserDevice touched(UserDevice device) {
        return new UserDevice(device.id(), device.nickname(), device.deviceName(), device.status(),
                TIME.format(LocalDateTime.now()), device.avatarText(), device.color(), device.imageAvatar(),
                device.host(), device.port(), userStatus(device.userStatus()), device.signature(), device.avatar());
    }

    // 解析设备在线状态
    private DeviceStatus status(String value) {
        try {
            return DeviceStatus.valueOf(value);
        } catch (Exception ignored) {
            return DeviceStatus.OFFLINE;
        }
    }

    // 解析用户在线状态
    private UserStatus userStatus(String value) {
        try {
            return UserStatus.valueOf(value);
        } catch (Exception ignored) {
            return UserStatus.DEFAULT;
        }
    }

    // 兜底用户在线状态
    private UserStatus userStatus(UserStatus status) {
        return status == null ? UserStatus.DEFAULT : status;
    }

    // 安全解析整型数值
    private int intValue(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
