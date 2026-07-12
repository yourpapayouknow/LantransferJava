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
    AutoStart() {
        this(defaultStartupDir(), isWindows());
    }
    AutoStart(Path startupDir) {
        this(startupDir, true);
    }
    private AutoStart(Path startupDir, boolean active) {
        this.startupDir = startupDir;
        this.active = active;
    }
    static AutoStart none() {
        return new AutoStart(null, false);
    }
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
    Path scriptPath() {
        return startupDir.resolve(SCRIPT_NAME);
    }
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
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
    private static Path defaultStartupDir() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                : Path.of(appData);
        return base.resolve("Microsoft").resolve("Windows").resolve("Start Menu").resolve("Programs").resolve("Startup");
    }
}
