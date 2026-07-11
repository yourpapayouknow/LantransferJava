package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.UserDevice;
import com.iwmei.lantransfer.model.UserStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// RecentStore 的无框架自检入口
public final class RecentStoreCheck {
    // 阻止自检类被实例化
    private RecentStoreCheck() {
    }

    // 运行近期对象保存、读取和去重置顶检查
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("lantransfer-recent-check", ".properties");
        Files.deleteIfExists(file);
        try {
            RecentStore store = new RecentStore(file);
            UserDevice first = device("u1", "张三", "PC-1");
            UserDevice second = device("u2", "李四", "PC-2");
            store.remember(List.of(first, second));
            require(store.load().size() == 2, "two recent devices should persist");
            store.remember(List.of(second));
            List<UserDevice> loaded = store.load();
            require(loaded.size() == 2, "duplicate recent device should not grow list");
            require("u2".equals(loaded.get(0).id()), "latest device should move to front");
            require(!loaded.get(0).lastSeen().isBlank(), "latest device should record transfer time");
            require(loaded.get(0).reachable(), "network address should persist");
            require(loaded.get(0).userStatus() == UserStatus.BUSY, "user status should persist");
            require("签名".equals(loaded.get(0).signature()), "signature should persist");
            require("QUJD".equals(loaded.get(0).avatar()), "avatar should persist");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    // 构造测试设备
    private static UserDevice device(String id, String nickname, String deviceName) {
        return new UserDevice(id, nickname, deviceName, DeviceStatus.ONLINE, "刚刚", nickname.substring(0, 1),
                "#4f7bd8", false, "127.0.0.1", 45332, UserStatus.BUSY, "签名", "QUJD");
    }

    // 断言条件为真
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
