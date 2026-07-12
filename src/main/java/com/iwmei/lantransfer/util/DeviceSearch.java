package com.iwmei.lantransfer.util;
import com.iwmei.lantransfer.model.UserDevice;
import java.util.Locale;
public final class DeviceSearch {
    private DeviceSearch() {
    }
    public static boolean matches(UserDevice device, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT).trim();
        return contains(device.nickname(), needle) || contains(device.deviceName(), needle)
                || contains(device.id(), needle) || contains(device.host(), needle) || contains(device.signature(), needle);
    }
    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
