package com.iwmei.lantransfer.service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

// Windows开机自启动脚本管理器，负责创建和删除启动目录脚本
final class AutoStart {
    private static final String SCRIPT_NAME = "极速互传.cmd";
    private final Path startupDir;
    private final boolean active;

    // 使用当前系统启动目录初始化自启动管理器
    AutoStart() {
        this(defaultStartupDir(), isWindows());
    }

    // 使用指定启动目录初始化自启动管理器
    AutoStart(Path startupDir) {
        this(startupDir, true);
    }

    // 使用指定目录和启用状态初始化自启动管理器
    private AutoStart(Path startupDir, boolean active) {
        this.startupDir = startupDir;
        this.active = active;
    }

    // 构造不产生系统副作用的自启动管理器
    static AutoStart none() {
        return new AutoStart(null, false);
    }

    // 按设置同步系统启动项
    void sync(boolean enabled) {
        if (!active || startupDir == null) {
            return;
        }
        try {
            if (enabled) {
                Files.createDirectories(startupDir);
                Files.writeString(scriptPath(), scriptBody(), StandardCharsets.UTF_8);
            } else {
                Files.deleteIfExists(scriptPath());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("同步开机自启动失败：" + startupDir, ex);
        }
    }

    // 返回启动脚本路径
    Path scriptPath() {
        return startupDir.resolve(SCRIPT_NAME);
    }

    // 构造启动脚本内容
    private String scriptBody() {
        String root = Path.of("").toAbsolutePath().normalize().toString();
        return String.join(System.lineSeparator(),
                "@echo off",
                "cd /d \"" + root + "\"",
                "if exist \"mvnw.cmd\" (",
                "  start \"\" /min \"mvnw.cmd\" -q javafx:run",
                ") else (",
                "  start \"\" /min mvn -q javafx:run",
                ")",
                "");
    }

    // 判断当前系统是否为Windows
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    // 返回Windows当前用户启动目录
    private static Path defaultStartupDir() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                : Path.of(appData);
        return base.resolve("Microsoft").resolve("Windows").resolve("Start Menu").resolve("Programs").resolve("Startup");
    }
}
