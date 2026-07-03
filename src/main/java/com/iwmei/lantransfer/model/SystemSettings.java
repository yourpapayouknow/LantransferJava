package com.iwmei.lantransfer.model;

public record SystemSettings(String ipv4, String ipv6, int uploadLimit, int downloadLimit, int maxRetries,
                             String accentColor, String fontFamily, int fontSize, int zoomPercent) {
}
