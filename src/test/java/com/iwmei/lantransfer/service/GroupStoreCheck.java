package com.iwmei.lantransfer.service;
import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.Group;
import com.iwmei.lantransfer.model.UserDevice;
import com.iwmei.lantransfer.model.UserStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// 传输分组仓库无框架自检
public final class GroupStoreCheck {
    // 执行分组保存、加载和展开检查
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("lantransfer-groups-check");
        try {
            GroupStore store = new GroupStore(dir.resolve("groups.properties"));
            UserDevice one = device("u1", "10.0.0.1", 5001);
            UserDevice two = device("u2", "10.0.0.2", 5002);
            UserDevice group = store.save("测试组", "1234", List.of(one, two, one));
            require(group.groupTarget(), "saved target should be a group");
            require("测试组".equals(group.groupName()), "group name should roundtrip");
            require(store.targets().size() == 1, "group target should load");
            Group loaded = store.all().get(0);
            require("1234".equals(loaded.code()), "group code should roundtrip");
            require(loaded.size() == 2, "group details should dedupe members");
            UserDevice renamed = store.update("测试组", "新组", "abcd", loaded.members());
            require("新组".equals(renamed.groupName()), "renamed target should use new group name");
            require(store.all().size() == 1, "rename should not leave old group");
            require("abcd".equals(store.all().get(0).code()), "updated code should roundtrip");
            List<UserDevice> expanded = store.expand(List.of(renamed, two));
            require(expanded.size() == 2, "group expansion should dedupe members");
            require(expanded.stream().allMatch(UserDevice::reachable), "members should keep network address");
            require(expanded.stream().anyMatch(item -> "签名".equals(item.signature()) && "QUJD".equals(item.avatar())),
                    "members should keep profile payload");
        } finally {
            deleteTree(dir);
        }
    }

    // 构造测试设备
    private static UserDevice device(String id, String host, int port) {
        return new UserDevice(id, id, id + "-pc", DeviceStatus.ONLINE, "刚刚", id.substring(0, 1),
                "#4f7bd8", false, host, port, UserStatus.DEFAULT, "签名", "QUJD");
    }

    // 检查条件，失败时抛出AssertionError
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // 删除临时目录树
    private static void deleteTree(Path root) throws Exception {
        if (Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
