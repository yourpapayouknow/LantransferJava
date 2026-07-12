package com.iwmei.lantransfer.service;
import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.SystemSettings;
import com.iwmei.lantransfer.model.TransferFile;
import com.iwmei.lantransfer.model.TransferSummary;
import com.iwmei.lantransfer.model.TransferTask;
import com.iwmei.lantransfer.model.UserDevice;
import com.iwmei.lantransfer.model.UserStatus;
import com.iwmei.lantransfer.util.FileIcons;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

// UDP文件发送服务，负责按目标设备地址发送文件并生成最终传输报告
final class UdpTx {
    private static final int DEFAULT_CHUNK_BYTES = 8192;
    private static final int ACK_TIMEOUT_MILLIS = 500;
    private static final int BEGIN_ACK_TIMEOUT_MILLIS = 15_000;
    private static final int MAX_CHUNK_WORKERS = 8;
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter DONE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final int chunkBytes;
    private final Object pauseLock = new Object();
    private volatile boolean paused;

    // 使用默认分片大小初始化发送服务
    UdpTx() {
        this(DEFAULT_CHUNK_BYTES);
    }

    // 使用指定分片大小初始化发送服务，供测试复用
    UdpTx(int chunkBytes) {
        this.chunkBytes = Math.max(512, chunkBytes);
    }

    // 暂停或继续后续UDP包发送
    void setPaused(boolean paused) {
        // 暂停状态同步
        synchronized (pauseLock) {
            this.paused = paused;
            if (!paused) {
                pauseLock.notifyAll();
            }
        }
    }

    // 发送文件到目标设备并返回传输汇总
    TransferSummary run(List<TransferFile> files, List<UserDevice> targets, SystemSettings settings) {
        return run(files, targets, settings, "", summary -> {
        });
    }

    // 发送文件到目标设备并在传输中推送进度快照
    TransferSummary run(List<TransferFile> files, List<UserDevice> targets, SystemSettings settings,
                        Consumer<TransferSummary> progress) {
        return run(files, targets, settings, "", progress);
    }

    // 按本次传输口令发送文件并推送进度快照
    TransferSummary run(List<TransferFile> files, List<UserDevice> targets, SystemSettings settings, String code,
                        Consumer<TransferSummary> progress) {
        long started = System.nanoTime();
        List<SourceFile> sources = sources(files == null ? List.of() : files);
        List<UserDevice> safeTargets = targets == null ? List.of() : targets;
        Consumer<TransferSummary> safeProgress = progress == null ? summary -> {
        } : progress;
        int maxRetries = settings == null ? 3 : Math.max(0, settings.maxRetries());
        long bytesPerSecond = perTargetBytesPerSecond(settings, sendableTargets(safeTargets));
        String codeHash = codeHash(code);
        List<TransferTask> tasks = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        int success = 0;
        int failed = 0;
        int retries = 0;
        logs.add(stamp("任务开始：共 " + safeTargets.size() + " 个目标，文件总数 " + sources.size() + " 个，大小 " + readable(totalBytes(sources))));

        // 空文件拦截
        if (sources.isEmpty()) {
            logs.add(stamp("⚠ 没有可传输文件"));
            return new TransferSummary(safeTargets.size(), 0, safeTargets.isEmpty() ? 0 : safeTargets.size(), 0,
                    elapsed(started), logs, List.of());
        }
        for (TargetSend sent : sendTargets(sources, safeTargets, maxRetries, bytesPerSecond, codeHash, safeProgress)) {
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
    private List<TargetSend> sendTargets(List<SourceFile> sources, List<UserDevice> targets, int maxRetries,
                                         long bytesPerSecond, String codeHash, Consumer<TransferSummary> progress) {
        if (targets.size() <= 1) {
            return targets.stream().map(target -> sendTarget(sources, target, maxRetries, bytesPerSecond, codeHash, progress,
                    targets.size())).toList();
        }
        int threads = Math.min(targets.size(), Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<TargetSend>> futures = new ArrayList<>();
            for (UserDevice target : targets) {
                futures.add(pool.submit(() -> sendTarget(sources, target, maxRetries, bytesPerSecond, codeHash, progress,
                        targets.size())));
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
    private TargetSend sendTarget(List<SourceFile> sources, UserDevice target, int maxRetries, long bytesPerSecond, String codeHash,
                                  Consumer<TransferSummary> progress, int targetCount) {
        List<TransferTask> tasks = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        int retries = 0;
        boolean success = sendable(target);
        if (!success) {
            for (SourceFile source : sources) {
                tasks.add(failedTask(source, target, 0));
            }
            logs.add(stamp("⚠ [" + targetLabel(target) + "] " + blockReason(target)));
            return new TargetSend(false, retries, tasks, logs);
        }
        String jobId = UUID.randomUUID().toString();
        RateLimit rate = new RateLimit(bytesPerSecond);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName(target.host()), target.port());
            socket.setSoTimeout(ACK_TIMEOUT_MILLIS);
            if (target.userStatus() == UserStatus.BUSY) {
                logs.add(stamp("… [" + targetLabel(target) + "] 对方忙碌，等待接收确认"));
            }
            for (int i = 0; i < sources.size(); i++) {
                FileSend sent = sendFile(socket, sources.get(i), target, jobId, i, maxRetries, rate, codeHash, progress,
                        targetCount);
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
    private FileSend sendFile(DatagramSocket socket, SourceFile source, UserDevice target, String jobId, int fileIndex,
                              int maxRetries, RateLimit rate, String codeHash, Consumer<TransferSummary> progress, int targetCount) {
        long started = System.nanoTime();
        List<String> logs = new ArrayList<>();
        int chunkCount = chunkCount(source.bytes());
        AckResult begin = sendText(socket, beginMessage(source, jobId, fileIndex, chunkCount, codeHash), jobId, fileIndex, -1,
                maxRetries, beginTimeout(target));
        int retries = begin.retries();
        if (!begin.success()) {
            logs.add(stamp("⚠ [" + targetLabel(target) + "] " + source.name() + " 发送请求未确认"));
            return new FileSend(false, retries, failedTask(source, target, retries), logs);
        }
        List<Integer> chunks = chunksToSend(begin.detail(), chunkCount);
        if (chunks.size() < chunkCount) {
            logs.add(stamp("↻ [" + targetLabel(target) + "] " + source.name() + " 断点续传：仅补发 "
                    + chunks.size() + " 个缺失分片"));
        }
        publishProgress(progress, targetCount, source, target, started, 0, 0, retries, logs);
        try (FileChannel input = FileChannel.open(source.path(), StandardOpenOption.READ)) {
            ChunkBatch batch = sendChunks(input, socket.getInetAddress(), socket.getPort(), source, target, jobId,
                    fileIndex, chunks, maxRetries, rate, progress, targetCount, started, retries, logs);
            retries += batch.retries();
            if (!batch.success()) {
                logs.add(stamp("⚠ [" + targetLabel(target) + "] " + source.name() + " "
                        + failedChunkLabel(batch.failedChunk()) + "未确认"));
                return new FileSend(false, retries, failedTask(source, target, retries), logs);
            }
            logs.add(stamp("✓ [" + targetLabel(target) + "] " + source.name() + " UDP 发送完成"));
            return new FileSend(true, retries, successTask(source, target, started, retries), logs);
        } catch (Exception ex) {
            logs.add(stamp("⚠ [" + targetLabel(target) + "] " + source.name() + " 读取或发送失败：" + ex.getMessage()));
            return new FileSend(false, retries, failedTask(source, target, retries), logs);
        }
    }

    // 并发发送单个文件的分片并汇总分片确认结果
    private ChunkBatch sendChunks(FileChannel input, InetAddress address, int port, SourceFile source, UserDevice target,
                                  String jobId, int fileIndex, List<Integer> chunks, int maxRetries, RateLimit rate,
                                  Consumer<TransferSummary> progress, int targetCount, long started, int retriesBefore,
                                  List<String> logs) {
        int workers = chunkWorkers(chunks.size());
        if (workers > 1) {
            addLog(logs, stamp("⇄ [" + targetLabel(target) + "] " + source.name() + " 分片并发："
                    + workers + " 个 worker"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        AtomicInteger cursor = new AtomicInteger();
        AtomicInteger failedChunk = new AtomicInteger(-1);
        AtomicInteger totalRetries = new AtomicInteger(retriesBefore);
        AtomicInteger nextProgressLog = new AtomicInteger(25);
        AtomicLong sentBytes = new AtomicLong();
        try {
            List<Future<WorkerSend>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                futures.add(pool.submit(() -> sendWorker(input, address, port, source, target, jobId, fileIndex, chunks,
                        maxRetries, rate, progress, targetCount, started, logs, cursor, failedChunk, totalRetries,
                        nextProgressLog, sentBytes)));
            }
            for (Future<WorkerSend> future : futures) {
                try {
                    WorkerSend sent = future.get();
                    if (!sent.success() && failedChunk.get() < 0) {
                        failedChunk.compareAndSet(-1, sent.failedChunk());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    failedChunk.compareAndSet(-1, -2);
                } catch (Exception ex) {
                    failedChunk.compareAndSet(-1, -2);
                }
            }
            return new ChunkBatch(failedChunk.get() < 0, totalRetries.get() - retriesBefore, failedChunk.get());
        } finally {
            pool.shutdownNow();
        }
    }

    // 单个worker使用自己的UDP socket发送多个分片
    private WorkerSend sendWorker(FileChannel input, InetAddress address, int port, SourceFile source, UserDevice target,
                                  String jobId, int fileIndex, List<Integer> chunks, int maxRetries, RateLimit rate,
                                  Consumer<TransferSummary> progress, int targetCount, long started, List<String> logs,
                                  AtomicInteger cursor, AtomicInteger failedChunk, AtomicInteger totalRetries,
                                  AtomicInteger nextProgressLog, AtomicLong sentBytes) {
        int localRetries = 0;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(address, port);
            socket.setSoTimeout(ACK_TIMEOUT_MILLIS);
            while (failedChunk.get() < 0) {
                int position = cursor.getAndIncrement();
                if (position >= chunks.size()) {
                    break;
                }
                int chunkIndex = chunks.get(position);
                ChunkSend sent = sendChunk(socket, input, jobId, fileIndex, chunkIndex, maxRetries);
                localRetries += sent.retries();
                totalRetries.addAndGet(sent.retries());
                if (!sent.success()) {
                    failedChunk.compareAndSet(-1, chunkIndex);
                    return new WorkerSend(false, localRetries, chunkIndex);
                }
                long done = sentBytes.addAndGet(sent.bytes());
                publishChunkProgress(progress, targetCount, source, target, started, done, totalRetries.get(), logs,
                        nextProgressLog);
                rate.pause(sent.bytes());
            }
            return new WorkerSend(true, localRetries, -1);
        } catch (Exception ignored) {
            failedChunk.compareAndSet(-1, -2);
            return new WorkerSend(false, localRetries, -2);
        }
    }

    // 读取并发送一个分片
    private ChunkSend sendChunk(DatagramSocket socket, FileChannel input, String jobId, int fileIndex, int chunkIndex,
                                int maxRetries) throws Exception {
        byte[] buffer = new byte[chunkBytes];
        int read = readChunk(input, chunkIndex, buffer);
        AckResult data = sendData(socket, buffer, read, jobId, fileIndex, chunkIndex, maxRetries);
        return new ChunkSend(data.success(), data.retries(), read);
    }

    // 根据已确认字节推送发送进度
    private void publishChunkProgress(Consumer<TransferSummary> progress, int targetCount, SourceFile source,
                                      UserDevice target, long started, long sentBytes, int retries, List<String> logs,
                                      AtomicInteger nextProgressLog) {
        int percent = progress(sentBytes, source.bytes());
        while (source.bytes() > 0) {
            int threshold = nextProgressLog.get();
            if (threshold >= 100 || percent < threshold) {
                return;
            }
            if (nextProgressLog.compareAndSet(threshold, threshold + 25)) {
                addLog(logs, stamp("… [" + targetLabel(target) + "] " + source.name() + " 进度 "
                        + threshold + "%，预计剩余 " + eta(started, sentBytes, source.bytes())));
                publishProgress(progress, targetCount, source, target, started, sentBytes, threshold, retries, logs);
            }
        }
    }

    // 推送当前文件的传输中快照给界面
    private void publishProgress(Consumer<TransferSummary> progress, int targetCount, SourceFile source,
                                 UserDevice target, long started, long sentBytes, int percent, int retries,
                                 List<String> logs) {
        try {
            String speed = sentBytes <= 0 ? "-" : speed(sentBytes, started);
            TransferTask task = new TransferTask(source.name(), target, percent, readable(source.bytes()), speed,
                    elapsed(started), "传输中", retries);
            progress.accept(new TransferSummary(targetCount, 0, 0, retries, elapsed(started), logSnapshot(logs),
                    List.of(task)));
        } catch (Exception ignored) {
        }
    }

    // 发送文本协议包并等待确认
    private AckResult sendText(DatagramSocket socket, String message, String jobId, int fileIndex, int chunkIndex, int maxRetries) {
        return sendText(socket, message, jobId, fileIndex, chunkIndex, maxRetries, ACK_TIMEOUT_MILLIS);
    }

    // 发送文本协议包并按指定超时等待确认
    private AckResult sendText(DatagramSocket socket, String message, String jobId, int fileIndex, int chunkIndex,
                               int maxRetries, int timeoutMillis) {
        return sendWithAck(socket, message.getBytes(StandardCharsets.UTF_8), jobId, fileIndex, chunkIndex, maxRetries,
                timeoutMillis);
    }

    // 发送二进制分片包并等待确认
    private AckResult sendData(DatagramSocket socket, byte[] chunk, int length, String jobId, int fileIndex, int chunkIndex, int maxRetries) {
        byte[] head = (UdpRx.DATA + "\t" + jobId + "\t" + fileIndex + "\t" + chunkIndex + "\t").getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(head.length + length);
        out.writeBytes(head);
        out.write(chunk, 0, length);
        return sendWithAck(socket, out.toByteArray(), jobId, fileIndex, chunkIndex, maxRetries, ACK_TIMEOUT_MILLIS);
    }

    // 发送数据包并按最大重试次数等待ACK
    private AckResult sendWithAck(DatagramSocket socket, byte[] data, String jobId, int fileIndex, int chunkIndex,
                                  int maxRetries, int timeoutMillis) {
        int retries = 0;
        int oldTimeout = ACK_TIMEOUT_MILLIS;
        try {
            oldTimeout = socket.getSoTimeout();
            socket.setSoTimeout(timeoutMillis);
        } catch (Exception ignored) {
        }
        try {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                if (attempt > 0) {
                    retries++;
                }
                if (!waitIfPaused()) {
                    return new AckResult(false, retries, "");
                }
                try {
                    socket.send(new DatagramPacket(data, data.length));
                    AckResult ack = awaitAck(socket, jobId, fileIndex, chunkIndex);
                    if (ack.success()) {
                        return new AckResult(true, retries, ack.detail());
                    }
                } catch (Exception ignored) {
                }
            }
            return new AckResult(false, retries, "");
        } finally {
            try {
                socket.setSoTimeout(oldTimeout);
            } catch (Exception ignored) {
            }
        }
    }

    // 等待并校验接收端ACK
    private AckResult awaitAck(DatagramSocket socket, String jobId, int fileIndex, int chunkIndex) {
        try {
            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            String[] parts = text.split("\t", 6);
            boolean ok = parts.length >= 5 && UdpRx.ACK.equals(parts[0]) && jobId.equals(parts[1])
                    && String.valueOf(fileIndex).equals(parts[2]) && String.valueOf(chunkIndex).equals(parts[3])
                    && "OK".equals(parts[4]);
            return new AckResult(ok, 0, ok && parts.length == 6 ? parts[5] : "");
        } catch (SocketTimeoutException ignored) {
            return new AckResult(false, 0, "");
        } catch (Exception ignored) {
            return new AckResult(false, 0, "");
        }
    }

    // 暂停状态下阻塞发送线程直到继续
    private boolean waitIfPaused() {
        // 暂停等待同步
        synchronized (pauseLock) {
            while (paused) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    // 构造文件开始协议包
    private String beginMessage(SourceFile source, String jobId, int fileIndex, int chunkCount, String codeHash) {
        return UdpRx.BEGIN + "\t" + jobId + "\t" + fileIndex + "\t" + encodeName(source.name())
                + "\t" + source.bytes() + "\t" + chunkCount + "\t" + chunkBytes + "\t" + source.sha256()
                + "\t" + codeHash;
    }

    // 读取一个固定大小文件分片
    private int readChunk(FileChannel input, int chunkIndex, byte[] buffer) throws Exception {
        long offset = (long) chunkIndex * chunkBytes;
        ByteBuffer chunk = ByteBuffer.wrap(buffer);
        while (chunk.hasRemaining()) {
            int read = input.read(chunk, offset + chunk.position());
            if (read < 0) {
                break;
            }
            if (read == 0) {
                break;
            }
        }
        return chunk.position();
    }

    // 计算单文件分片并发worker数量
    private int chunkWorkers(int chunks) {
        if (chunks <= 1) {
            return 1;
        }
        return Math.min(chunks, Math.min(MAX_CHUNK_WORKERS, Math.max(2, Runtime.getRuntime().availableProcessors())));
    }

    // 生成分片失败展示文本
    private String failedChunkLabel(int chunkIndex) {
        return chunkIndex < 0 ? "未知分片" : "第 " + chunkIndex + " 个分片";
    }

    // 添加一条线程安全日志
    private void addLog(List<String> logs, String line) {
        // 日志写入同步
        synchronized (logs) {
            logs.add(line);
        }
    }

    // 复制当前日志快照
    private List<String> logSnapshot(List<String> logs) {
        // 日志快照同步
        synchronized (logs) {
            return List.copyOf(logs);
        }
    }

    // 解析接收端返回的缺失分片列表
    private List<Integer> chunksToSend(String missing, int chunkCount) {
        if (missing == null || missing.isBlank()) {
            return range(chunkCount);
        }
        List<Integer> chunks = new ArrayList<>();
        for (String item : missing.split(",")) {
            try {
                int index = Integer.parseInt(item.trim());
                if (index >= 0 && index < chunkCount) {
                    chunks.add(index);
                }
            } catch (Exception ignored) {
            }
        }
        return chunks.isEmpty() ? range(chunkCount) : chunks;
    }

    // 返回从零开始的分片索引列表
    private List<Integer> range(int count) {
        List<Integer> chunks = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            chunks.add(index);
        }
        return chunks;
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
            } else if (Files.isRegularFile(file.path()) && FileIcons.supported(file.path())) {
                sources.add(new SourceFile(file.fileName(), file.path(), sizeOf(file.path()), sha256(file.path())));
            }
        }
        return sources;
    }

    // 把文件夹展开为多个真实文件
    private void addDirectory(List<SourceFile> sources, TransferFile file) {
        Path root = file.path();
        try (var paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && FileIcons.supported(path)).forEach(path -> {
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

    // 判断目标是否允许直接发送
    private boolean sendable(UserDevice target) {
        return online(target) && target.reachable() && allowedStatus(target.userStatus());
    }

    // 判断用户状态是否允许直接发送
    private boolean allowedStatus(UserStatus status) {
        UserStatus value = status == null ? UserStatus.DEFAULT : status;
        return value == UserStatus.DEFAULT || value == UserStatus.ONLINE || value == UserStatus.BUSY;
    }

    // 返回文件开始包确认等待时间
    private int beginTimeout(UserDevice target) {
        return BEGIN_ACK_TIMEOUT_MILLIS;
    }

    // 生成目标拦截原因
    private String blockReason(UserDevice target) {
        if (target == null) {
            return "目标为空，未执行 UDP 发送";
        }
        if (!online(target)) {
            return "设备离线，未执行 UDP 发送";
        }
        if (!target.reachable()) {
            return "设备不可达，未执行 UDP 发送";
        }
        UserStatus status = target.userStatus() == null ? UserStatus.DEFAULT : target.userStatus();
        if (status == UserStatus.INVISIBLE || status == UserStatus.OFFLINE) {
            return "对方当前状态不允许接收文件，已拦截发送";
        }
        return "目标不满足传输条件，未执行 UDP 发送";
    }

    // 统计可真实发送的目标数量
    private int sendableTargets(List<UserDevice> targets) {
        int count = 0;
        for (UserDevice target : targets) {
            if (sendable(target)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    // 计算每个目标分到的上传限速字节数
    long perTargetBytesPerSecond(SystemSettings settings, int targetCount) {
        int limit = settings == null ? 0 : settings.uploadLimit();
        if (limit <= 0) {
            return 0;
        }
        return Math.max(1L, limit * 1024L * 1024L / Math.max(1, targetCount));
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

    // 计算文件SHA-256
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
        return formatSeconds(seconds);
    }

    // 按已发送字节估算剩余时间
    private String eta(long started, long sent, long total) {
        if (sent <= 0 || total <= sent) {
            return "00:00:00";
        }
        long elapsedMillis = Math.max(1, (System.nanoTime() - started) / 1_000_000);
        long remainingMillis = Math.max(0, (total - sent) * elapsedMillis / sent);
        return formatSeconds((long) Math.ceil(remainingMillis / 1000.0));
    }

    // 计算发送进度百分比
    private int progress(long sent, long total) {
        return total <= 0 ? 100 : (int) Math.min(100, sent * 100 / total);
    }

    // 格式化秒数为时间文本
    private String formatSeconds(long seconds) {
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

    // 计算本次传输口令摘要
    private String codeHash(String code) {
        String value = code == null ? "" : code.trim();
        if (value.isBlank()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ignored) {
            return "";
        }
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
    private record AckResult(boolean success, int retries, String detail) {
    }

    // 单个分片发送结果
    private record ChunkSend(boolean success, int retries, int bytes) {
    }

    // 单个worker发送结果
    private record WorkerSend(boolean success, int retries, int failedChunk) {
    }

    // 单个文件所有分片发送结果
    private record ChunkBatch(boolean success, int retries, int failedChunk) {
    }

    // 简单目标级限速器，按已发送字节和目标速率短暂休眠
    private static final class RateLimit {
        private final long bytesPerSecond;
        private final long started = System.nanoTime();
        private long sent;

        // 初始化目标级限速器
        private RateLimit(long bytesPerSecond) {
            this.bytesPerSecond = bytesPerSecond;
        }

        // 根据已发送字节数等待到目标速率
        private synchronized void pause(int bytes) {
            if (bytesPerSecond <= 0 || bytes <= 0) {
                return;
            }
            sent += bytes;
            long expected = (long) (sent * 1_000_000_000.0 / bytesPerSecond);
            long wait = expected - (System.nanoTime() - started);
            if (wait <= 0) {
                return;
            }
            try {
                Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
