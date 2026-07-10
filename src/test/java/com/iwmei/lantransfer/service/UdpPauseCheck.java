package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.SystemSettings;
import com.iwmei.lantransfer.model.TransferFile;
import com.iwmei.lantransfer.model.TransferSummary;
import com.iwmei.lantransfer.model.UserDevice;
import com.iwmei.lantransfer.model.UserStatus;

import java.net.DatagramSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// UDP 暂停发送无框架自检
public final class UdpPauseCheck {
    // 执行暂停阻塞和继续完成检查
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("lantransfer-pause-check");
        try {
            Path receive = Files.createDirectories(dir.resolve("rx"));
            SettingsStore settings = new SettingsStore(dir.resolve("settings.properties"));
            settings.save(new SystemSettings("127.0.0.1", "::1", 0, 0, 3, "#ff8500", "Microsoft YaHei",
                    14, 100, receive.toString(), "", "简体中文", false, false, false));
            int port = freePort();
            UdpRx rx = new UdpRx(settings, port);
            rx.start();
            Path source = dir.resolve("pause.txt");
            Files.writeString(source, "pause check");
            UdpTx tx = new UdpTx(512);
            tx.setPaused(true);
            UserDevice target = new UserDevice("rx", "rx", "rx-pc", DeviceStatus.ONLINE, "刚刚", "R",
                    "#4f7bd8", false, "127.0.0.1", port, UserStatus.DEFAULT);
            CompletableFuture<TransferSummary> future = CompletableFuture.supplyAsync(() ->
                    tx.run(List.of(new TransferFile("pause.txt", "11 B", source)), List.of(target), settings.load()));
            Thread.sleep(300);
            require(!future.isDone(), "paused transfer should not finish");
            tx.setPaused(false);
            TransferSummary summary = future.get(5, TimeUnit.SECONDS);
            require(summary.successCount() == 1, "continued transfer should finish");
            require("pause check".equals(Files.readString(receive.resolve("pause.txt"))), "received file should match");
        } finally {
            deleteTree(dir);
        }
    }

    // 获取一个临时可用 UDP 端口
    private static int freePort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    // 检查条件，失败时抛出 AssertionError
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // 删除临时目录树
    private static void deleteTree(Path root) throws Exception {
        if (Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
