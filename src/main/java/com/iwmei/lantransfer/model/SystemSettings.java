package com.iwmei.lantransfer.model;
import java.nio.file.Path;
public record SystemSettings(String ipv4, String ipv6, int uploadLimit, int downloadLimit, int maxRetries,
                             String accentColor, String fontFamily, int fontSize, int zoomPercent,
                             String receiveDir, String groupCode, String language, boolean autoStart,
                             boolean startMinimized, boolean soundOnComplete) {
    public SystemSettings(String ipv4, String ipv6, int uploadLimit, int downloadLimit, int maxRetries,
                          String accentColor, String fontFamily, int fontSize, int zoomPercent) {
        this(ipv4, ipv6, uploadLimit, downloadLimit, maxRetries, accentColor, fontFamily, fontSize, zoomPercent,
                defaultReceiveDir(), "", "简体中文", false, false, true);
    }
    public static String defaultReceiveDir() {
        return Path.of(System.getProperty("user.home"), "极速互传", "接收文件").toString();
    }
}
