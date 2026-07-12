package com.iwmei.lantransfer.service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
final class AppFiles {
    private AppFiles() {
    }
    static Path dataDir() {
        String override = configuredDataDir();
        if (!override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".lantransfer", repoSlug());
    }
    private static String configuredDataDir() {
        String property = System.getProperty("lantransfer.dataDir", "").trim();
        if (!property.isBlank()) {
            return property;
        }
        return System.getenv().getOrDefault("LANTRANSFER_DATA_DIR", "").trim();
    }
    static String repoSlug() {
        String origin = repoOrigin();
        int slash = origin.lastIndexOf('/');
        String name = slash >= 0 ? origin.substring(slash + 1) : origin;
        return name.replace(".git", "").replaceAll("[^A-Za-z0-9_.-]", "_");
    }
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
