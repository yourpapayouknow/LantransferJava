package com.iwmei.lantransfer.util;

import com.iwmei.lantransfer.model.UserDevice;

import java.util.Locale;

// 设备搜索工具，负责按用户输入匹配设备基础字段
public final class DeviceSearch {
    // 阻止工具类被实例化
    private DeviceSearch() {
    }

    // 判断设备是否匹配搜索词
    public static boolean matches(UserDevice device, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT).trim();
        return contains(device.nickname(), needle) || contains(device.deviceName(), needle)
                || contains(device.id(), needle) || contains(device.host(), needle);
    }

    // 判断文本是否包含已规范化搜索词
    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
