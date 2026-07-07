package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.UserDevice;

// LanPeer 的无框架自检入口
public final class LanPeerCheck {
    // 阻止自检类被实例化
    private LanPeerCheck() {
    }

    // 运行协议编码、解析和本机设备兜底检查
    public static void main(String[] args) {
        LanPeer peer = new LanPeer(false);
        UserDevice source = new UserDevice("D-1", "李四", "PC-1", DeviceStatus.ONLINE, "刚刚", "李", "#4f7bd8", false);
        UserDevice parsed = peer.parse(peer.encode(source));
        require(parsed != null, "encoded peer should parse");
        require("D-1".equals(parsed.id()), "id should round trip");
        require("李四".equals(parsed.nickname()), "nickname should round trip");
        require(parsed.status() == DeviceStatus.ONLINE, "parsed peer should be online");
        require(parsed.reachable(), "parsed peer should include transfer address");
        require(!peer.knownDevices().isEmpty(), "local device should exist");
    }

    // 断言条件为真
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
