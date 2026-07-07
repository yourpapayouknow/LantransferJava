package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.SystemSettings;
import com.iwmei.lantransfer.model.TransferFile;
import com.iwmei.lantransfer.model.TransferSummary;
import com.iwmei.lantransfer.model.TransferTask;
import com.iwmei.lantransfer.model.UserDevice;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// UDP 文件发送服务，负责按目标设备地址发送文件并生成最终传输报告
final class UdpTx {
    private static final int DEFAULT_CHUNK_BYTES = 8192;
    private static final int ACK_TIMEOUT_MILLIS = 500;
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter DONE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final int chunkBytes;

    // 使用默认分片大小初始化发送服务
    UdpTx() {
        this(DEFAULT_CHUNK_BYTES);
    }

    // 使用指定分片大小初始化发送服务，供测试复用
    UdpTx(int chunkBytes) {
        this.chunkBytes = Math.max(512, chunkBytes);
    }

    // 发送文件到目标设备并返回传输汇总
    TransferSummary run(List<TransferFile> files, List<UserDevice> targets, SystemSettings settings) {
        long started = System.nanoTime();
        List<SourceFile> sources = sources(files == null ? List.of() : files);
        List<UserDevice> safeTargets = targets == null ? List.of() : targets;
        int maxRetries = settings == null ? 3 : Math.max(0, settings.maxRetries());
        List<TransferTask> tasks = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        int success = 0;
        int failed = 0;
        int retries = 0;
        logs.add(stamp("任务开始：共 " + safeTargets.size() + " 个目标，文件总数 " + sources.size() + " 个，大小 " + readable(totalBytes(sources))));
        for (TargetSend sent : sendTargets(sources, safeTargets, maxRetries)) {
            tasks.addAll(sent.tasks());
            logs.addAll(sent.logs());
            retries += sent.retries();
            if (sent.success()) {
                success++;
            } else {
                failed++;
            }
        }
        logs.add(stamp("任务结束：成功 " + success + "，失败 " + failed + "，重试 " + retries));
        return new TransferSummary(safeTargets.size(), success, failed, retries, elapsed(started), logs, tasks);
    }

    // 并发发送到多个目标并按原目标顺序返回结果
    private List<TargetSend> sendTargets(List<SourceFile> sources, List<UserDevice> targets, int maxRetries) {
        if (targets.size() <= 1) {
            return targets.stream().map(target -> sendTarget(sources, target, maxRetries)).toList();
        }
        int threads = Math.min(targets.size(), Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<TargetSend>> futures = new ArrayList<>();
            for (UserDevice target : targets) {
                futures.add(pool.submit(() -> sendTarget(sources, target, maxRetries)));
            }
            List<TargetSend> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    results.add(futures.get(i).get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    results.add(failedTarget(sources, targets.get(i), maxRetries, "发送线程被中断"));
                } catch (Exception ex) {
                    results.add(failedTarget(sources, targets.get(i), maxRetries, "发送线程失败：" + ex.getMessage()));
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    // 发送所有文件到单个目标
    private TargetSend sendTarget(List<SourceFile> sources, UserDevice target, int maxRetries) {
        List<TransferTask> tasks = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        int retries = 0;
        boolean success = online(target) && target.reachable();
        if (!success) {
            for (SourceFile source : sources) {
                tasks.add(failedTask(source, target, 0));
            }
            logs.add(stamp("⚠ [" + targetLabel(target) + "] 设备不可达，未执行 UDP 发送"));
            return new TargetSend(false, retries, tasks, logs);
        }
        String jobId = UUID.randomUUID().toString();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName(target.host()), target.port());
            socket.setSoTimeout(ACK_TIMEOUT_MILLIS);
            for (int i = 0; i < sources.size(); i++) {
                FileSend sent = sendFile(socket, sources.get(i), target, jobId, i, maxRetries);
                tasks.add(sent.task());
                logs.addAll(sent.logs());
                retries += sent.retries();
                success = success && sent.success();
            }
        } catch (Exception ex) {
            success = false;
            for (SourceFile source : sources) {
                tasks.add(failedTask(source, target, maxRetries));
            }
            retries += maxRetries;
            logs.add(stamp("⚠ [" + targetLabel(target) + "] UDP 发送初始化失败：" + ex.getMessage()));
        }
        return new TargetSend(success, retries, tasks, logs);
    }

    // 构造目标级异常失败结果
    private TargetSend failedTarget(List<SourceFile> sources, UserDevice target, int retries, String reason) {
        List<TransferTask> tasks = new ArrayList<>();
        for (SourceFile source : sources) {
            tasks.add(failedTask(source, target, retries));
        }
        return new TargetSend(false, retries, tasks, List.of(stamp("⚠ [" + targetLabel(target) + "] " + reason)));
    }

    // 发送单个文件
    private FileSend sendFile(DatagramSocket socket, SourceFile source, UserDevice target, String jobId, int fileIndex, int maxRetries) {
        long started = System.nanoTime();
        List<String> logs = new ArrayList<>();
        int chunkCount = chunkCount(source.bytes());
        AckResult begin = sendText(socket, beginMessage(source, jobId, fileIndex, chunkCount), jobId, fileIndex, -1, maxRetries);
        int retries = begin.retries();
        if (!begin.success()) {
            logs.add(stamp("⚠ [" + targetLabel(target) + "] " + source.name() + " 发送请求未确认"));
            return new FileSend(false, retries, failedTask(source, target, retries), logs);
        }
        try (InputStream input = Files.newInputStream(source.path())) {
            byte[] buffer = new byte[chunkBytes];
            for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                int read = readChunk(input, buffer);
                AckResult data = sendData(socket, buffer, read, jobId, fileIndex, chunkIndex, maxRetries);
                retries += data.retries();
                if (!data.success()) {
                    logs.add(stamp("⚠ [" + targetLabel(target) + "] " + source.name() + " 第 " + chunkIndex + " 个分片未确认"));
                    return new FileSend(false, retries, failedTask(source, target, retries), logs);
                }
            }
            logs.add(stamp("✓ [" + targetLabel(target) + "] " + source.name() + " UDP 发送完成"));
            return new FileSend(true, retries, successTask(source, target, started, retries), logs);
        } catch (Exception ex) {
            logs.add(stamp("⚠ [" + targetLabel(target) + "] " + source.name() + " 读取或发送失败：" + ex.getMessage()));
            return new FileSend(false, retries, failedTask(source, target, retries), logs);
        }
    }

    // 发送文本协议包并等待确认
    private AckResult sendText(DatagramSocket socket, String message, String jobId, int fileIndex, int chunkIndex, int maxRetries) {
        return sendWithAck(socket, message.getBytes(StandardCharsets.UTF_8), jobId, fileIndex, chunkIndex, maxRetries);
    }

    // 发送二进制分片包并等待确认
    private AckResult sendData(DatagramSocket socket, byte[] chunk, int length, String jobId, int fileIndex, int chunkIndex, int maxRetries) {
        byte[] head = (UdpRx.DATA + "\t" + jobId + "\t" + fileIndex + "\t" + chunkIndex + "\t").getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(head.length + length);
        out.writeBytes(head);
        out.write(chunk, 0, length);
        return sendWithAck(socket, out.toByteArray(), jobId, fileIndex, chunkIndex, maxRetries);
    }

    // 发送数据包并按最大重试次数等待 ACK
    private AckResult sendWithAck(DatagramSocket socket, byte[] data, String jobId, int fileIndex, int chunkIndex, int maxRetries) {
        int retries = 0;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                retries++;
            }
            try {
                socket.send(new DatagramPacket(data, data.length));
                if (awaitAck(socket, jobId, fileIndex, chunkIndex)) {
                    return new AckResult(true, retries);
                }
            } catch (Exception ignored) {
            }
        }
        return new AckResult(false, retries);
    }

    // 等待并校验接收端 ACK
    private boolean awaitAck(DatagramSocket socket, String jobId, int fileIndex, int chunkIndex) {
        try {
            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            String[] parts = text.split("\t", 5);
            return parts.length == 5 && UdpRx.ACK.equals(parts[0]) && jobId.equals(parts[1])
                    && String.valueOf(fileIndex).equals(parts[2]) && String.valueOf(chunkIndex).equals(parts[3])
                    && "OK".equals(parts[4]);
        } catch (SocketTimeoutException ignored) {
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    // 构造文件开始协议包
    private String beginMessage(SourceFile source, String jobId, int fileIndex, int chunkCount) {
        return UdpRx.BEGIN + "\t" + jobId + "\t" + fileIndex + "\t" + encodeName(source.name())
                + "\t" + source.bytes() + "\t" + chunkCount + "\t" + chunkBytes + "\t" + source.sha256();
    }

    // 读取一个固定大小文件分片
    private int readChunk(InputStream input, byte[] buffer) throws Exception {
        int offset = 0;
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                break;
            }
            offset += read;
        }
        return offset;
    }

    // 收集待发送的真实文件
    private List<SourceFile> sources(List<TransferFile> files) {
        List<SourceFile> sources = new ArrayList<>();
        for (TransferFile file : files) {
            if (file == null || file.path() == null || !Files.exists(file.path())) {
                continue;
            }
            if (Files.isDirectory(file.path())) {
                addDirectory(sources, file);
            } else if (Files.isRegularFile(file.path())) {
                sources.add(new SourceFile(file.fileName(), file.path(), sizeOf(file.path()), sha256(file.path())));
            }
        }
        return sources;
    }

    // 把文件夹展开为多个真实文件
    private void addDirectory(List<SourceFile> sources, TransferFile file) {
        Path root = file.path();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String relative = root.relativize(path).toString().replace('\\', '/');
                sources.add(new SourceFile(file.fileName() + "/" + relative, path, sizeOf(path), sha256(path)));
            });
        } catch (Exception ignored) {
        }
    }

    // 构造成功任务行
    private TransferTask successTask(SourceFile source, UserDevice target, long started, int retries) {
        return new TransferTask(source.name(), target, 100, readable(source.bytes()), speed(source.bytes(), started),
                DONE_TIME.format(LocalDateTime.now()), "已完成", retries);
    }

    // 构造失败任务行
    private TransferTask failedTask(SourceFile source, UserDevice target, int retries) {
        return new TransferTask(source.name(), target, 0, readable(source.bytes()), "-",
                DONE_TIME.format(LocalDateTime.now()), "传输失败", retries);
    }

    // 判断目标是否在线
    private boolean online(UserDevice target) {
        return target != null && target.status() == DeviceStatus.ONLINE;
    }

    // 生成目标展示名
    private String targetLabel(UserDevice target) {
        if (target == null) {
            return "未知设备";
        }
        return target.nickname() + "(" + target.deviceName() + ")";
    }

    // 计算分片数量
    private int chunkCount(long bytes) {
        return bytes == 0 ? 0 : (int) Math.ceil(bytes / (double) chunkBytes);
    }

    // 读取文件大小
    private long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ignored) {
            return 0;
        }
    }

    // 计算文件 SHA-256
    private String sha256(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[chunkBytes];
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ignored) {
            return "";
        }
    }

    // 汇总待发送字节数
    private long totalBytes(List<SourceFile> sources) {
        long total = 0;
        for (SourceFile source : sources) {
            total += source.bytes();
        }
        return total;
    }

    // 格式化整体耗时
    private String elapsed(long started) {
        long millis = Math.max(1, (System.nanoTime() - started) / 1_000_000);
        long seconds = Math.max(1, (long) Math.ceil(millis / 1000.0));
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    // 格式化单文件速度
    private String speed(long bytes, long started) {
        long millis = Math.max(1, (System.nanoTime() - started) / 1_000_000);
        return readable(bytes * 1000 / millis) + "/s";
    }

    // 格式化字节大小
    private String readable(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.ROOT, "%.2f MB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024) {
            return String.format(Locale.ROOT, "%.2f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    // 编码文件名
    private String encodeName(String value) {
        return Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    // 给日志加时间戳
    private String stamp(String message) {
        return "[" + LOG_TIME.format(LocalTime.now()) + "]  " + message;
    }

    // 待发送的真实文件
    private record SourceFile(String name, Path path, long bytes, String sha256) {
    }

    // 单个文件发送结果
    private record FileSend(boolean success, int retries, TransferTask task, List<String> logs) {
    }

    // 单个目标发送结果
    private record TargetSend(boolean success, int retries, List<TransferTask> tasks, List<String> logs) {
    }

    // 单个数据包确认结果
    private record AckResult(boolean success, int retries) {
    }
}
