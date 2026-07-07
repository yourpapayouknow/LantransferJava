package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.SystemSettings;
import com.iwmei.lantransfer.model.TransferFile;
import com.iwmei.lantransfer.model.TransferSummary;
import com.iwmei.lantransfer.model.UserDevice;

import java.net.DatagramSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

// UdpTx 和 UdpRx 的本机真实 UDP 自检入口
public final class UdpWireCheck {
    // 阻止自检类被实例化
    private UdpWireCheck() {
    }

    // 运行本机 UDP 发送、确认和接收落盘检查
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("lantransfer-udp-check");
        Path receiveDir = root.resolve("rx");
        Path settingsFile = root.resolve("settings.properties");
        Path source = root.resolve("hello.txt");
        try {
            Files.writeString(source, "hello udp");
            SettingsStore store = new SettingsStore(settingsFile);
            store.save(new SystemSettings("127.0.0.1", "::1", 10, 20, 2, "#ff8500", "Microsoft YaHei", 14, 100,
                    receiveDir.toString(), "简体中文", false, true, true));
            int port = freePort();
            new UdpRx(store, port).start();
            Thread.sleep(120);
            UserDevice target = new UserDevice("self", "本机", "TEST-PC", DeviceStatus.ONLINE, "刚刚", "本",
                    "#4f7bd8", false, "127.0.0.1", port);
            TransferSummary summary = new UdpTx(1024).run(List.of(new TransferFile("hello.txt", "9 B", source)),
                    List.of(target), store.load());
            Path received = receiveDir.resolve("hello.txt");
            require(summary.successCount() == 1, "UDP target should succeed");
            require(summary.failedCount() == 0, "UDP target should not fail");
            require(Files.exists(received), "received file should exist");
            require("hello udp".equals(Files.readString(received)), "received content should match");
        } finally {
            deleteTree(root);
        }
    }

    // 获取一个临时可用 UDP 端口
    private static int freePort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    // 删除临时目录树
    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    // 断言条件为真
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
