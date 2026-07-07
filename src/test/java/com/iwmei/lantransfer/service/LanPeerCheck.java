package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.UserDevice;
import com.iwmei.lantransfer.model.UserStatus;

// LanPeer 的无框架自检入口
public final class LanPeerCheck {
    // 阻止自检类被实例化
    private LanPeerCheck() {
    }

    // 运行协议编码、解析和本机设备兜底检查
    public static void main(String[] args) throws Exception {
        LanPeer peer = new LanPeer(false, 1);
        UserDevice source = new UserDevice("D-1", "李四", "PC-1", DeviceStatus.ONLINE, "刚刚", "李", "#4f7bd8", false,
                "127.0.0.1", 45332, UserStatus.BUSY);
        UserDevice parsed = peer.parse(peer.encode(source));
        require(parsed != null, "encoded peer should parse");
        require("D-1".equals(parsed.id()), "id should round trip");
        require("李四".equals(parsed.nickname()), "nickname should round trip");
        require(parsed.status() == DeviceStatus.ONLINE, "parsed peer should be online");
        require(parsed.userStatus() == UserStatus.BUSY, "user status should round trip");
        require(parsed.reachable(), "parsed peer should include transfer address");
        peer.updateGroup("team-a");
        String teamMessage = peer.encode(source);
        require(peer.parse(teamMessage) != null, "same group should parse");
        peer.updateGroup("team-b");
        require(peer.parse(teamMessage) == null, "different group should be ignored");
        require(!peer.knownDevices().isEmpty(), "local device should exist");
        peer.remember(parsed);
        require(device(peer, "D-1").status() == DeviceStatus.ONLINE, "remembered peer should start online");
        Thread.sleep(5);
        require(device(peer, "D-1").status() == DeviceStatus.OFFLINE, "expired peer should become offline");
    }

    // 按 ID 读取已发现设备
    private static UserDevice device(LanPeer peer, String id) {
        return peer.knownDevices().stream().filter(item -> id.equals(item.id())).findFirst().orElseThrow();
    }

    // 断言条件为真
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
