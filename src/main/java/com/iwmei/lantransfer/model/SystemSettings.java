package com.iwmei.lantransfer.model;

import java.nio.file.Path;

// 系统设置数据对象
public record SystemSettings(String ipv4, String ipv6, int uploadLimit, int downloadLimit, int maxRetries,
                             String accentColor, String fontFamily, int fontSize, int zoomPercent,
                             String receiveDir, String language, boolean autoStart, boolean startMinimized,
                             boolean soundOnComplete) {
    // 使用基础字段构造系统设置
    public SystemSettings(String ipv4, String ipv6, int uploadLimit, int downloadLimit, int maxRetries,
                          String accentColor, String fontFamily, int fontSize, int zoomPercent) {
        this(ipv4, ipv6, uploadLimit, downloadLimit, maxRetries, accentColor, fontFamily, fontSize, zoomPercent,
                defaultReceiveDir(), "简体中文", false, true, true);
    }

    // 返回默认接收目录
    public static String defaultReceiveDir() {
        return Path.of(System.getProperty("user.home"), "极速互传", "接收文件").toString();
    }
}
