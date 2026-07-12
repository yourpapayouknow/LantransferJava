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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// UdpTx和UdpRx的本机真实UDP自检入口
public final class UdpWireCheck {
    // 阻止自检类被实例化
    private UdpWireCheck() {
    }

    // 运行本机UDP发送、确认和接收落盘检查
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("lantransfer-udp-check");
        Path receiveDir = root.resolve("rx");
        Path settingsFile = root.resolve("settings.properties");
        Path source = root.resolve("hello.txt");
        Path etaSource = root.resolve("eta.txt");
        Path resumeSource = root.resolve("resume.bin");
        Path busySource = root.resolve("busy.txt");
        Path secureSource = root.resolve("secure.txt");
        Path secureSecond = root.resolve("secure2.txt");
        Path secureLarge = root.resolve("secure-large.bin");
        Path folder = root.resolve("folder");
        try {
            Files.writeString(source, "hello udp");
            Files.writeString(etaSource, "a".repeat(1024));
            String resumeContent = "b".repeat(1024);
            Files.writeString(resumeSource, resumeContent);
            Files.writeString(busySource, "busy confirm");
            Files.writeString(secureSource, "secure confirm");
            Files.writeString(secureSecond, "secure confirm 2");
            Files.write(secureLarge, "L".repeat(1024 * 1024).getBytes(StandardCharsets.UTF_8));
            Files.createDirectories(folder.resolve("sub"));
            Files.writeString(folder.resolve("a.txt"), "folder a");
            Files.writeString(folder.resolve("sub").resolve("b.txt"), "folder b");
            List<TransferFile> parallelFiles = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Path path = root.resolve("parallel-" + i + ".txt");
                Files.writeString(path, "parallel " + i);
                parallelFiles.add(new TransferFile(path.getFileName().toString(), "10 B", path));
            }
            List<TransferFile> largeParallelFiles = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Path path = root.resolve("parallel-large-" + i + ".bin");
                Files.write(path, ("P" + i).repeat(32 * 1024).getBytes(StandardCharsets.UTF_8));
                largeParallelFiles.add(new TransferFile(path.getFileName().toString(), "64.00 KB", path));
            }
            SettingsStore store = new SettingsStore(settingsFile);
            store.save(new SystemSettings("127.0.0.1", "::1", 10, 20, 2, "#ff8500", "Microsoft YaHei", 14, 100,
                    receiveDir.toString(), "", "简体中文", false, true, true));
            int port = freePort();
            List<String> rxProgress = Collections.synchronizedList(new ArrayList<>());
            UdpRx rx = new UdpRx(store, port);
            rx.setProgress((name, percent) -> rxProgress.add(name + ":" + percent));
            rx.start();
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
            require(rxProgress.stream().anyMatch(item -> item.equals("hello.txt:100")),
                    "receiver progress should publish completion");
            List<TransferSummary> folderProgress = new ArrayList<>();
            TransferSummary folderSummary = tx.run(List.of(new TransferFile("folder", "文件夹", folder)),
                    List.of(first), store.load(), "", folderProgress::add);
            require(folderSummary.successCount() == 1, "folder zip transfer should succeed");
            require(folderProgress.stream().anyMatch(snapshot -> snapshot.tasks().stream()
                            .anyMatch(task -> "folder.zip".equals(task.fileName()) && "传输中".equals(task.status()))),
                    "folder transfer should publish initial running task: " + folderProgress);
            require("folder a".equals(Files.readString(receiveDir.resolve("folder").resolve("a.txt"))),
                    "folder root file should be restored");
            require("folder b".equals(Files.readString(receiveDir.resolve("folder").resolve("sub").resolve("b.txt"))),
                    "folder nested file should be restored");
            require(!Files.exists(receiveDir.resolve("folder.zip")), "folder package should be removed after unzip");
            UserDevice third = new UserDevice("self-parallel-3", "本机C", "TEST-PC", DeviceStatus.ONLINE, "刚刚", "本",
                    "#7a52d8", false, "127.0.0.1", port);
            UserDevice fourth = new UserDevice("self-parallel-4", "本机D", "TEST-PC", DeviceStatus.ONLINE, "刚刚", "本",
                    "#2f9f62", false, "127.0.0.1", port);
            TransferSummary parallel = new UdpTx().run(parallelFiles, List.of(first, second, third, fourth), store.load());
            require(parallel.successCount() == 4, "four targets should send in parallel successfully");
            require(parallel.tasks().size() == 20, "five files times four targets should create twenty file-target tasks");
            require(parallel.logs().stream().anyMatch(log -> log.contains("文件目标并发：20 个任务")),
                    "file-target parallelism should be logged: " + parallel.logs());
            TransferSummary largeParallel = new UdpTx(1024).run(largeParallelFiles, List.of(first, second), store.load());
            require(largeParallel.successCount() == 2, "large parallel targets should succeed: " + largeParallel.logs());
            require(largeParallel.tasks().size() == 4, "two large files times two targets should create four tasks");
            require(largeParallel.logs().stream().noneMatch(log -> log.contains("分片并发")),
                    "multi-object transfer should not stack chunk workers: " + largeParallel.logs());
            int busyAcceptPort = freePort();
            UdpRx busyAcceptRx = new UdpRx(store, busyAcceptPort);
            busyAcceptRx.updateStatus(UserStatus.BUSY);
            busyAcceptRx.setAsk((name, bytes, codeHash) -> true);
            busyAcceptRx.start();
            Thread.sleep(120);
            UserDevice busy = new UserDevice("self-3", "本机C", "TEST-PC", DeviceStatus.ONLINE, "刚刚", "本",
                    "#7a52d8", false, "127.0.0.1", busyAcceptPort, UserStatus.BUSY);
            TransferSummary acceptedBusy = tx.run(List.of(new TransferFile("busy.txt", "12 B", busySource)),
                    List.of(busy), store.load());
            require(acceptedBusy.successCount() == 1, "busy target should send after receiver approval");
            require(acceptedBusy.logs().stream().anyMatch(log -> log.contains("等待接收确认")),
                    "busy confirmation wait should be logged");
            require("busy confirm".equals(Files.readString(receiveDir.resolve("busy.txt"))),
                    "approved busy receive should land file");
            int staleBusyPort = freePort();
            UdpRx staleBusyRx = new UdpRx(store, staleBusyPort);
            staleBusyRx.updateStatus(UserStatus.BUSY);
            staleBusyRx.setAsk((name, bytes, codeHash) -> {
                try {
                    Thread.sleep(2200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                return true;
            });
            staleBusyRx.start();
            Thread.sleep(120);
            UserDevice staleBusy = new UserDevice("self-stale", "本机缓存旧状态", "TEST-PC", DeviceStatus.ONLINE, "刚刚", "本",
                    "#7a52d8", false, "127.0.0.1", staleBusyPort);
            TransferSummary staleBusyAccepted = tx.run(List.of(new TransferFile("stale-busy.txt", "12 B", busySource)),
                    List.of(staleBusy), store.load());
            require(staleBusyAccepted.successCount() == 1, "stale default target should wait for busy receiver approval");
            require("busy confirm".equals(Files.readString(receiveDir.resolve("stale-busy.txt"))),
                    "approved stale busy receive should land file");
            int busyDenyPort = freePort();
            UdpRx busyDenyRx = new UdpRx(store, busyDenyPort);
            busyDenyRx.updateStatus(UserStatus.BUSY);
            busyDenyRx.setAsk((name, bytes, codeHash) -> false);
            busyDenyRx.start();
            Thread.sleep(120);
            UserDevice busyDeny = new UserDevice("self-4", "本机D", "TEST-PC", DeviceStatus.ONLINE, "刚刚", "本",
                    "#7a52d8", false, "127.0.0.1", busyDenyPort, UserStatus.BUSY);
            TransferSummary deniedBusy = tx.run(List.of(new TransferFile("deny.txt", "12 B", busySource)),
                    List.of(busyDeny), store.load());
            require(deniedBusy.failedCount() == 1, "busy target should fail after receiver rejection");
            require(!Files.exists(receiveDir.resolve("deny.txt")), "rejected busy receive should not land file");
            int securePort = freePort();
            UdpRx secureRx = new UdpRx(store, securePort);
            String secretHash = sha256("secret");
            AtomicInteger secureAsks = new AtomicInteger();
            List<String> secureNames = Collections.synchronizedList(new ArrayList<>());
            secureRx.setAsk((name, bytes, codeHash) -> {
                secureNames.add(name);
                secureAsks.incrementAndGet();
                return secretHash.equals(codeHash);
            });
            secureRx.start();
            Thread.sleep(120);
            UserDevice secureTarget = new UserDevice("self-secure", "本机E", "TEST-PC", DeviceStatus.ONLINE, "刚刚", "本",
                    "#2f9f62", false, "127.0.0.1", securePort);
            TransferSummary secureAccepted = tx.run(List.of(new TransferFile("secure.txt", "14 B", secureSource),
                            new TransferFile("secure2.txt", "16 B", secureSecond)),
                    List.of(secureTarget), store.load(), "secret", ignored -> {
                    });
            require(secureAccepted.successCount() == 1, "correct transfer code should allow receive");
            require(secureAsks.get() == 1, "same transfer job should ask code once: " + secureNames);
            TransferSummary secureLargeAccepted = new UdpTx().run(List.of(new TransferFile("secure-large.bin", "1.00 MB", secureLarge)),
                    List.of(secureTarget), store.load(), "secret", ignored -> {
                    });
            require(secureLargeAccepted.successCount() == 1, "large transfer with code should succeed: " + secureLargeAccepted.logs());
            require(secureLargeAccepted.retryCount() < 8,
                    "large transfer with code should not rely on heavy retries: " + secureLargeAccepted.retryCount());
            TransferSummary secureDenied = tx.run(List.of(new TransferFile("secure-deny.txt", "14 B", secureSource)),
                    List.of(secureTarget), store.load(), "wrong", ignored -> {
                    });
            require(secureDenied.failedCount() == 1, "wrong transfer code should reject receive");
            List<TransferSummary> progressSnapshots = new ArrayList<>();
            TransferSummary etaSummary = new UdpTx(512).run(List.of(new TransferFile("eta.txt", "1.00 KB", etaSource)),
                    List.of(first), store.load(), progressSnapshots::add);
            require(etaSummary.logs().stream().anyMatch(log -> log.contains("预计剩余")),
                    "ETA should be logged during chunk send: " + etaSummary.logs());
            require(etaSummary.logs().stream().anyMatch(log -> log.contains("分片并发")),
                    "chunk-level concurrency should be logged: " + etaSummary.logs());
            require(progressSnapshots.stream().anyMatch(snapshot -> snapshot.tasks().stream()
                            .anyMatch(task -> "传输中".equals(task.status()) && task.progressPercent() >= 25)),
                    "progress callback should publish running snapshots");
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
            require(sendUnsupportedBegin(port).endsWith("\tFAIL\tUNSUPPORTED"), "unsupported type should fail");
        } finally {
            deleteTree(root);
        }
    }

    // 发送一个不支持类型的开始包并返回接收端确认
    private static String sendUnsupportedBegin(int port) throws Exception {
        String name = Base64.getUrlEncoder().encodeToString("bad.exe".getBytes(StandardCharsets.UTF_8));
        String message = UdpRx.BEGIN + "\tunsupported-job\t0\t" + name + "\t0\t0\t1024\t" + sha256("");
        return sendAndReceive(port, message.getBytes(StandardCharsets.UTF_8));
    }

    // 发送一个SHA-256错误的开始包并返回接收端确认
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

    // 发送一个UDP包并返回接收端确认
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

    // 计算测试内容SHA-256
    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    // 获取一个临时可用UDP端口
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
