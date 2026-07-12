package com.iwmei.lantransfer.service;
import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.Group;
import com.iwmei.lantransfer.model.UserDevice;
import com.iwmei.lantransfer.model.UserStatus;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// 本地传输分组仓库，负责保存分组成员快照并把组目标展开成真实用户
final class GroupStore {
    private final Path store;

    // 使用默认用户目录分组文件初始化仓库
    GroupStore() {
        this(AppFiles.dataDir().resolve("groups.properties"));
    }

    // 使用指定分组文件初始化仓库，供自检复用
    GroupStore(Path store) {
        this.store = store;
    }

    // 保存一个分组并返回可加入近期对象的组目标
    synchronized UserDevice save(String name, String code, List<UserDevice> members) {
        String groupName = UserDevice.cleanGroupName(name);
        List<UserDevice> cleanMembers = cleanMembers(members);
        if (cleanMembers.isEmpty()) {
            throw new IllegalArgumentException("分组至少需要一个用户");
        }
        Map<String, Group> groups = loadGroups();
        groups.put(groupName, new Group(groupName, code, cleanMembers));
        writeGroups(groups);
        return UserDevice.group(groupName, cleanMembers.size());
    }

    // 更新已有分组并返回新的组目标
    synchronized UserDevice update(String oldName, String name, String code, List<UserDevice> members) {
        String groupName = UserDevice.cleanGroupName(name);
        List<UserDevice> cleanMembers = cleanMembers(members);
        if (cleanMembers.isEmpty()) {
            throw new IllegalArgumentException("分组至少需要一个用户");
        }
        Map<String, Group> groups = loadGroups();
        groups.remove(UserDevice.cleanGroupName(oldName));
        groups.put(groupName, new Group(groupName, code, cleanMembers));
        writeGroups(groups);
        return UserDevice.group(groupName, cleanMembers.size());
    }

    // 加载全部分组详情供用户列表展示
    synchronized List<Group> all() {
        return new ArrayList<>(loadGroups().values());
    }

    // 加载所有可显示在近期对象中的分组目标
    synchronized List<UserDevice> targets() {
        return loadGroups().values().stream()
                .map(Group::target)
                .toList();
    }

    // 把传输目标中的分组展开为真实成员
    synchronized List<UserDevice> expand(List<UserDevice> targets) {
        Map<String, Group> groups = loadGroups();
        Map<String, UserDevice> expanded = new LinkedHashMap<>();
        for (UserDevice target : targets == null ? List.<UserDevice>of() : targets) {
            if (target == null) {
                continue;
            }
            if (!target.groupTarget()) {
                expanded.putIfAbsent(target.id(), target);
                continue;
            }
            Group group = groups.get(target.groupName());
            if (group == null || group.members().isEmpty()) {
                expanded.putIfAbsent(target.id(), target);
                continue;
            }
            for (UserDevice member : group.members()) {
                expanded.putIfAbsent(member.id(), member);
            }
        }
        return new ArrayList<>(expanded.values());
    }

    // 清洗成员列表并按用户ID去重
    private List<UserDevice> cleanMembers(List<UserDevice> members) {
        Map<String, UserDevice> clean = new LinkedHashMap<>();
        for (UserDevice member : members == null ? List.<UserDevice>of() : members) {
            if (member != null && !member.groupTarget() && !member.id().isBlank()) {
                clean.putIfAbsent(member.id(), member);
            }
        }
        return new ArrayList<>(clean.values());
    }

    // 读取分组文件中的全部分组
    private Map<String, Group> loadGroups() {
        Map<String, Group> groups = new LinkedHashMap<>();
        if (!Files.exists(store)) {
            return groups;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(store, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException ignored) {
            return groups;
        }
        int count = intValue(props.getProperty("count"), 0);
        for (int group = 0; group < count; group++) {
            String name = UserDevice.cleanGroupName(props.getProperty(group + ".name", ""));
            String code = props.getProperty(group + ".code", "");
            int members = intValue(props.getProperty(group + ".memberCount"), 0);
            List<UserDevice> devices = new ArrayList<>();
            for (int member = 0; member < members; member++) {
                UserDevice device = device(props, group + ".member." + member + ".");
                if (device != null) {
                    devices.add(device);
                }
            }
            if (!devices.isEmpty()) {
                groups.put(name, new Group(name, code, devices));
            }
        }
        return groups;
    }

    // 写回全部分组
    private void writeGroups(Map<String, Group> groups) {
        Properties props = new Properties();
        props.setProperty("repo.origin", AppFiles.repoOrigin());
        props.setProperty("count", String.valueOf(groups.size()));
        int group = 0;
        for (Group entry : groups.values()) {
            props.setProperty(group + ".name", entry.name());
            props.setProperty(group + ".code", entry.code());
            props.setProperty(group + ".memberCount", String.valueOf(entry.members().size()));
            for (int member = 0; member < entry.members().size(); member++) {
                put(props, group + ".member." + member + ".", entry.members().get(member));
            }
            group++;
        }
        try {
            Files.createDirectories(store.getParent());
            try (Writer writer = Files.newBufferedWriter(store, StandardCharsets.UTF_8)) {
                props.store(writer, "Lantransfer groups");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("保存分组失败：" + store, ex);
        }
    }

    // 保存单个成员字段
    private void put(Properties props, String prefix, UserDevice device) {
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

    // 读取单个成员字段
    private UserDevice device(Properties props, String prefix) {
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
