package com.iwmei.lantransfer.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// 应用本地数据路径工具，负责把运行数据放到用户目录而不是项目仓库
final class AppFiles {
    // 阻止工具类被实例化
    private AppFiles() {
    }

    // 返回当前仓库命名空间下的数据目录，允许测试实例用参数覆盖
    static Path dataDir() {
        String override = configuredDataDir();
        if (!override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".lantransfer", repoSlug());
    }

    // 读取多实例测试或虚拟机联调指定的数据目录
    private static String configuredDataDir() {
        String property = System.getProperty("lantransfer.dataDir", "").trim();
        if (!property.isBlank()) {
            return property;
        }
        return System.getenv().getOrDefault("LANTRANSFER_DATA_DIR", "").trim();
    }

    // 读取 GitHub 远程仓库名作为本地数据命名空间
    static String repoSlug() {
        String origin = repoOrigin();
        int slash = origin.lastIndexOf('/');
        String name = slash >= 0 ? origin.substring(slash + 1) : origin;
        return name.replace(".git", "").replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    // 读取当前仓库 origin 地址
    static String repoOrigin() {
        Path config = Path.of(".git", "config");
        if (!Files.exists(config)) {
            return "LantransferJava";
        }
        try {
            boolean inOrigin = false;
            for (String line : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[remote ")) {
                    inOrigin = trimmed.equals("[remote \"origin\"]");
                } else if (inOrigin && trimmed.startsWith("url =")) {
                    return trimmed.substring("url =".length()).trim();
                }
            }
        } catch (IOException ignored) {
            return "LantransferJava";
        }
        return "LantransferJava";
    }
}
