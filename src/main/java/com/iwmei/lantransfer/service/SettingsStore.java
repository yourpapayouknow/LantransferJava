package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.SystemSettings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Properties;

// 本地设置仓库，负责系统设置的读取和保存
final class SettingsStore {
    private final Path store;

    // 使用默认用户目录设置文件初始化仓库
    SettingsStore() {
        this(AppFiles.dataDir().resolve("settings.properties"));
    }

    // 使用指定设置文件初始化仓库，供测试复用
    SettingsStore(Path store) {
        this.store = store;
    }

    // 加载系统设置
    synchronized SystemSettings load() {
        SystemSettings defaults = defaults();
        if (!Files.exists(store)) {
            return defaults;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(store, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException ignored) {
            return defaults;
        }
        return new SystemSettings(
                props.getProperty("ipv4", defaults.ipv4()),
                props.getProperty("ipv6", defaults.ipv6()),
                intValue(props, "uploadLimit", defaults.uploadLimit()),
                intValue(props, "downloadLimit", defaults.downloadLimit()),
                intValue(props, "maxRetries", defaults.maxRetries()),
                props.getProperty("accentColor", defaults.accentColor()),
                props.getProperty("fontFamily", defaults.fontFamily()),
                intValue(props, "fontSize", defaults.fontSize()),
                intValue(props, "zoomPercent", defaults.zoomPercent())
        );
    }

    // 保存系统设置
    synchronized void save(SystemSettings settings) {
        SystemSettings value = settings == null ? defaults() : settings;
        Properties props = new Properties();
        props.setProperty("repo.origin", AppFiles.repoOrigin());
        props.setProperty("ipv4", value.ipv4());
        props.setProperty("ipv6", value.ipv6());
        props.setProperty("uploadLimit", String.valueOf(value.uploadLimit()));
        props.setProperty("downloadLimit", String.valueOf(value.downloadLimit()));
        props.setProperty("maxRetries", String.valueOf(value.maxRetries()));
        props.setProperty("accentColor", value.accentColor());
        props.setProperty("fontFamily", value.fontFamily());
        props.setProperty("fontSize", String.valueOf(value.fontSize()));
        props.setProperty("zoomPercent", String.valueOf(value.zoomPercent()));
        try {
            Files.createDirectories(store.getParent());
            try (Writer writer = Files.newBufferedWriter(store, StandardCharsets.UTF_8)) {
                props.store(writer, "Lantransfer settings");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("保存设置文件失败：" + store, ex);
        }
    }

    // 构造默认系统设置
    private SystemSettings defaults() {
        return new SystemSettings(localIp(false), localIp(true), 10, 20, 3, "#ff8500", "Microsoft YaHei", 14, 100);
    }

    // 读取整数设置
    private int intValue(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    // 获取本机 IP 地址
    private String localIp(boolean ipv6) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface item = interfaces.nextElement();
                if (!item.isUp() || item.isLoopback() || item.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = item.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && (ipv6 ? address instanceof Inet6Address : address instanceof Inet4Address)) {
                        return address.getHostAddress().split("%", 2)[0];
                    }
                }
            }
        } catch (Exception ignored) {
            return ipv6 ? "::1" : "127.0.0.1";
        }
        return ipv6 ? "::1" : "127.0.0.1";
    }
}
