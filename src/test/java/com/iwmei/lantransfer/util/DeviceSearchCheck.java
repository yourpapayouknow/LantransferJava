package com.iwmei.lantransfer.util;

import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.UserDevice;

// DeviceSearch 的无框架自检入口
public final class DeviceSearchCheck {
    // 阻止自检类被实例化
    private DeviceSearchCheck() {
    }

    // 运行昵称、设备名、ID 和地址匹配检查
    public static void main(String[] args) {
        UserDevice device = new UserDevice("D-1001", "张三", "DESKTOP-A", DeviceStatus.ONLINE, "刚刚",
                "张", "#4f7bd8", false, "192.168.1.8", 45332, com.iwmei.lantransfer.model.UserStatus.DEFAULT,
                "局域网签名", "");
        require(DeviceSearch.matches(device, "张"), "nickname should match");
        require(DeviceSearch.matches(device, "desktop"), "device name should match ignoring case");
        require(DeviceSearch.matches(device, "1001"), "id should match");
        require(DeviceSearch.matches(device, "192.168"), "host should match");
        require(DeviceSearch.matches(device, "签名"), "signature should match");
        require(!DeviceSearch.matches(device, "李四"), "unrelated query should not match");
    }

    // 断言条件为真
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
