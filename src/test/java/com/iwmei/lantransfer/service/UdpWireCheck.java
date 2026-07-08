package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.SystemSettings;
import com.iwmei.lantransfer.model.TransferFile;
import com.iwmei.lantransfer.model.TransferSummary;
import com.iwmei.lantransfer.model.UserDevice;
import com.iwmei.lantransfer.model.UserStatus;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
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
        Path etaSource = root.resolve("eta.txt");
        Path resumeSource = root.resolve("resume.bin");
        try {
            Files.writeString(source, "hello udp");
            Files.writeString(etaSource, "a".repeat(1024));
            String resumeContent = "b".repeat(1024);
            Files.writeString(resumeSource, resumeContent);
            SettingsStore store = new SettingsStore(settingsFile);
            store.save(new SystemSettings("127.0.0.1", "::1", 10, 20, 2, "#ff8500", "Microsoft YaHei", 14, 100,
                    receiveDir.toString(), "", "简体中文", false, true, true));
            int port = freePort();
            new UdpRx(store, port).start();
            Thread.sleep(120);
            UserDevice first = new UserDevice("self-1", "本机A", "TEST-PC", DeviceStatus.ONLINE, "刚刚", "本",
                    "#4f7bd8", false, "127.0.0.1", port);
            UserDevice second = new UserDevice("self-2", "本机B", "TEST-PC", DeviceStatus.ONLINE, "刚刚", "本",
                    "#35c6ca", false, "127.0.0.1", port);
            UdpTx tx = new UdpTx(1024);
            require(tx.perTargetBytesPerSecond(store.load(), 2) == 5L * 1024 * 1024, "upload limit should split by target count");
            TransferSummary summary = tx.run(List.of(new TransferFile("hello.txt", "9 B", source)),
                    List.of(first, second), store.load());
            Path firstFile = receiveDir.resolve("hello.txt");
            Path secondFile = receiveDir.resolve("hello-1.txt");
            require(summary.successCount() == 2, "UDP targets should succeed");
            require(summary.failedCount() == 0, "UDP target should not fail");
            require(Files.exists(firstFile), "first received file should exist");
            require(Files.exists(secondFile), "second received file should exist");
            require("hello udp".equals(Files.readString(firstFile)), "first received content should match");
            require("hello udp".equals(Files.readString(secondFile)), "second received content should match");
            UserDevice busy = new UserDevice("self-3", "本机C", "TEST-PC", DeviceStatus.ONLINE, "刚刚", "本",
                    "#7a52d8", false, "127.0.0.1", port, UserStatus.BUSY);
            TransferSummary blocked = tx.run(List.of(new TransferFile("hello.txt", "9 B", source)), List.of(busy), store.load());
            require(blocked.successCount() == 0, "busy target should be blocked");
            require(blocked.failedCount() == 1, "busy target should count as failed");
            require(blocked.logs().stream().anyMatch(log -> log.contains("对方忙碌")), "busy block reason should be logged");
            TransferSummary etaSummary = new UdpTx(512).run(List.of(new TransferFile("eta.txt", "1.00 KB", etaSource)), List.of(first), store.load());
            require(etaSummary.logs().stream().anyMatch(log -> log.contains("预计剩余")),
                    "ETA should be logged during chunk send: " + etaSummary.logs());
            require(sendPartial(port).endsWith("\tOK"), "partial chunk should be accepted");
            require(Files.readString(receiveDir.resolve("partial.bin.part.meta")).contains("received=0"),
                    "partial metadata should persist received chunk index");
            int resumeSeedPort = freePort();
            new UdpRx(store, resumeSeedPort).start();
            Thread.sleep(120);
            require(sendResumePartial(resumeSeedPort, resumeContent).endsWith("\tOK"), "resume seed chunk should be accepted");
            require(Files.readString(receiveDir.resolve("resume.bin.part.meta")).contains("received=0"),
                    "resume metadata should persist first chunk");
            int resumeProbePort = freePort();
            new UdpRx(store, resumeProbePort).start();
            Thread.sleep(120);
            require(sendResumeBegin(resumeProbePort, "resume-probe", resumeContent).endsWith("\tOK\t1"),
                    "resumed begin should report missing chunk index");
            int resumePort = freePort();
            new UdpRx(store, resumePort).start();
            Thread.sleep(120);
            UserDevice resumeTarget = new UserDevice("self-4", "本机D", "TEST-PC", DeviceStatus.ONLINE, "刚刚", "本",
                    "#2f9f62", false, "127.0.0.1", resumePort);
            TransferSummary resumed = new UdpTx(512).run(List.of(new TransferFile("resume.bin", "1.00 KB", resumeSource)),
                    List.of(resumeTarget), store.load());
            require(resumed.successCount() == 1, "missing chunk resend should succeed");
            require(resumed.logs().stream().anyMatch(log -> log.contains("断点续传")),
                    "missing chunk resend should be logged: " + resumed.logs());
            require(resumeContent.equals(Files.readString(receiveDir.resolve("resume.bin"))),
                    "resumed file content should match source");
            require(sendBadChecksumBegin(port).endsWith("\tFAIL"), "bad checksum should fail");
            require(!Files.exists(receiveDir.resolve("bad.txt")), "bad checksum file should not land");
        } finally {
            deleteTree(root);
        }
    }

    // 发送一个 SHA-256 错误的开始包并返回接收端确认
    private static String sendBadChecksumBegin(int port) throws Exception {
        String name = Base64.getUrlEncoder().encodeToString("bad.txt".getBytes(StandardCharsets.UTF_8));
        String message = UdpRx.BEGIN + "\tbad-job\t0\t" + name + "\t0\t0\t1024\tbad-sha";
        return sendAndReceive(port, message.getBytes(StandardCharsets.UTF_8));
    }

    // 发送一个未完成文件的首个分片并返回接收端确认
    private static String sendPartial(int port) throws Exception {
        String name = Base64.getUrlEncoder().encodeToString("partial.bin".getBytes(StandardCharsets.UTF_8));
        String begin = UdpRx.BEGIN + "\tpartial-job\t0\t" + name + "\t8\t2\t4\t" + sha256("abcdefgh");
        require(sendAndReceive(port, begin.getBytes(StandardCharsets.UTF_8)).endsWith("\tOK"), "partial begin should ack");
        String data = UdpRx.DATA + "\tpartial-job\t0\t0\tabcd";
        return sendAndReceive(port, data.getBytes(StandardCharsets.UTF_8));
    }

    // 发送一个已完成首片的续传种子文件并返回分片确认
    private static String sendResumePartial(int port, String content) throws Exception {
        require(sendResumeBegin(port, "resume-seed", content).endsWith("\tOK"), "resume begin should ack");
        String data = UdpRx.DATA + "\tresume-seed\t0\t0\t" + content.substring(0, 512);
        return sendAndReceive(port, data.getBytes(StandardCharsets.UTF_8));
    }

    // 发送续传文件开始包并返回接收端确认
    private static String sendResumeBegin(int port, String jobId, String content) throws Exception {
        String name = Base64.getUrlEncoder().encodeToString("resume.bin".getBytes(StandardCharsets.UTF_8));
        String begin = UdpRx.BEGIN + "\t" + jobId + "\t0\t" + name + "\t" + content.length() + "\t2\t512\t"
                + sha256(content);
        return sendAndReceive(port, begin.getBytes(StandardCharsets.UTF_8));
    }

    // 发送一个 UDP 包并返回接收端确认
    private static String sendAndReceive(int port, byte[] data) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(1000);
            socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), port));
            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            return new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        }
    }

    // 计算测试内容 SHA-256
    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
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
