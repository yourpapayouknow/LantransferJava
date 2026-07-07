package com.iwmei.lantransfer.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

// AutoStart 的无框架自检入口
public final class AutoStartCheck {
    // 阻止自检类被实例化
    private AutoStartCheck() {
    }

    // 运行启动脚本创建和删除检查
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("lantransfer-autostart-check");
        try {
            AutoStart autoStart = new AutoStart(dir);
            autoStart.sync(true);
            require(Files.exists(autoStart.scriptPath()), "startup script should be created");
            String body = Files.readString(autoStart.scriptPath(), StandardCharsets.UTF_8);
            require(body.contains("javafx:run"), "startup script should launch JavaFX app");
            autoStart.sync(false);
            require(!Files.exists(autoStart.scriptPath()), "startup script should be removed");
        } finally {
            delete(dir);
        }
    }

    // 断言条件为真
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // 删除临时自检目录
    private static void delete(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
