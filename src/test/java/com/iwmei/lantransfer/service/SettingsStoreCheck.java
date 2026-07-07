package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.SystemSettings;

import java.nio.file.Files;
import java.nio.file.Path;

// SettingsStore 的无框架自检入口
public final class SettingsStoreCheck {
    // 阻止自检类被实例化
    private SettingsStoreCheck() {
    }

    // 运行默认设置和保存后读取检查
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("lantransfer-settings-check", ".properties");
        Files.deleteIfExists(file);
        try {
            SettingsStore store = new SettingsStore(file);
            SystemSettings defaults = store.load();
            require(defaults.maxRetries() == 3, "default retries should be 3");
            SystemSettings saved = new SystemSettings("1.1.1.1", "::1", 1, 2, 4, "#2f80ed", "Arial", 16, 125,
                    SystemSettings.defaultReceiveDir(), "team-a", "简体中文", false, true, true);
            store.save(saved);
            SystemSettings loaded = store.load();
            require(loaded.uploadLimit() == 1, "upload limit should persist");
            require(loaded.maxRetries() == 4, "retry count should persist");
            require("#2f80ed".equals(loaded.accentColor()), "accent color should persist");
            require(loaded.zoomPercent() == 125, "zoom should persist");
            require("team-a".equals(loaded.groupCode()), "group code should persist");
            require(!loaded.receiveDir().isBlank(), "receive dir should exist");
            require("简体中文".equals(loaded.language()), "language should persist");
            require(loaded.soundOnComplete(), "sound flag should persist");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    // 断言条件为真
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
