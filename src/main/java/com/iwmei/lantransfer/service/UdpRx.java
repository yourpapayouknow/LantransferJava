package com.iwmei.lantransfer.service;
import com.iwmei.lantransfer.model.UserStatus;
import com.iwmei.lantransfer.util.FileIcons;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// UDP文件接收服务，负责后台监听传输端口并把收到的文件落盘到接收目录
final class UdpRx {
    static final String BEGIN = "LANTRANSFER_FILE_BEGIN_V1";
    static final String DATA = "LANTRANSFER_FILE_DATA_V1";
    static final String ACK = "LANTRANSFER_FILE_ACK_V1";
    private static final int BUFFER_BYTES = 60_000;
    private static final int RX_WORKERS = 8;
    private final SettingsStore settings;
    private final int port;
    private final Map<String, RxFile> active = new ConcurrentHashMap<>();
    private final Map<String, Boolean> approvals = new ConcurrentHashMap<>();
    private final Set<Path> reserved = ConcurrentHashMap.newKeySet();
    private final RateLimit downloadRate = new RateLimit();
    // 接收worker池
    private final ExecutorService workers = Executors.newFixedThreadPool(RX_WORKERS, task -> {
        Thread thread = new Thread(task, "lantransfer-udp-rx-worker");
        thread.setDaemon(true);
        return thread;
    });
    private volatile UserStatus status = UserStatus.DEFAULT;
    private volatile RxAsk ask = (fileName, bytes, codeHash) -> true;
    private volatile RxProgress progress = (fileName, percent) -> {
    };
    private volatile boolean running;

    // 使用默认传输端口初始化接收服务
    UdpRx(SettingsStore settings) {
        this(settings, LanPeer.TRANSFER_PORT);
    }

    // 使用指定端口初始化接收服务
    UdpRx(SettingsStore settings, int port) {
        this.settings = settings;
        this.port = port;
    }

    // 启动后台接收线程
    synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        Thread thread = new Thread(this::listen, "lantransfer-udp-rx");
        thread.setDaemon(true);
        thread.start();
    }

    // 更新本机接收状态
    void updateStatus(UserStatus status) {
        this.status = status == null ? UserStatus.DEFAULT : status;
    }

    // 设置接收前确认回调
    void setAsk(RxAsk ask) {
        this.ask = ask == null ? (fileName, bytes, codeHash) -> true : ask;
    }

    // 设置接收进度回调
    void setProgress(RxProgress progress) {
        this.progress = progress == null ? (fileName, percent) -> {
        } : progress;
    }

    // 持续监听UDP文件传输数据包
    private void listen() {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            // 接收缓冲
            socket.setReceiveBufferSize(BUFFER_BYTES * RX_WORKERS * 8);
            socket.bind(new InetSocketAddress(port));
            while (running) {
                byte[] buffer = new byte[BUFFER_BYTES];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handle(socket, packet);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // 按协议类型处理接收到的数据包
    private void handle(DatagramSocket socket, DatagramPacket packet) {
        byte[] data = packet.getData();
        int length = packet.getLength();
        if (startsWith(data, length, BEGIN)) {
            handleBegin(socket, packet);
        } else if (startsWith(data, length, DATA)) {
            // DATA并行处理
            workers.execute(() -> handleData(socket, packet));
        }
    }

    // 处理文件开始包并创建接收状态
    private void handleBegin(DatagramSocket socket, DatagramPacket packet) {
        String[] parts = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).split("\t", 10);
        if (parts.length < 7) {
            return;
        }
        String jobId = parts[1];
        int fileIndex = intValue(parts[2], -1);
        String key = key(jobId, fileIndex);
        boolean ok = false;
        String detail = "";
        if (active.containsKey(key)) {
            ack(socket, packet, jobId, fileIndex, -1, true, active.get(key).missing());
            return;
        }
        try {
            String fileName = decodeName(parts[3]);
            long size = longValue(parts[4], -1);
            int chunkCount = intValue(parts[5], -1);
            int chunkSize = intValue(parts[6], -1);
            String sha256 = parts.length >= 8 ? parts[7] : "";
            String codeHash = parts.length >= 9 ? parts[8] : "";
            boolean folderZip = parts.length >= 10 && "DIRZIP".equals(parts[9]);
            if (!FileIcons.supportedName(fileName)) {
                detail = "UNSUPPORTED";
            } else if (allowBegin(jobId, fileName, size, codeHash)) {
                RxFile file = createFile(fileName, size, chunkCount, chunkSize, sha256, folderZip);
                active.put(key, file);
                detail = file.missing();
                if (chunkCount == 0) {
                    file.finish();
                }
                ok = true;
            } else {
                detail = "REJECTED";
            }
        } catch (Exception ignored) {
        }
        ack(socket, packet, jobId, fileIndex, -1, ok, detail);
    }

    // 判断当前状态是否允许开始接收文件
    private boolean allowBegin(String jobId, String fileName, long size, String codeHash) {
        UserStatus value = status == null ? UserStatus.DEFAULT : status;
        if (value == UserStatus.INVISIBLE || value == UserStatus.OFFLINE) {
            return false;
        }
        boolean needsAsk = value == UserStatus.BUSY || codeHash != null && !codeHash.isBlank();
        if (!needsAsk) {
            return true;
        }
        try {
            return approvals.computeIfAbsent(approvalKey(jobId, codeHash), key -> ask.approve(fileName, size, codeHash));
        } catch (Exception ignored) {
            return false;
        }
    }

    // 生成本次传输确认缓存键
    private String approvalKey(String jobId, String codeHash) {
        String code = codeHash == null ? "" : codeHash;
        return code.isBlank() ? jobId : code;
    }

    // 处理文件内容分片并在收齐后移动到最终文件
    private void handleData(DatagramSocket socket, DatagramPacket packet) {
        byte[] data = packet.getData();
        int length = packet.getLength();
        int offset = dataOffset(data, length, 4);
        if (offset < 0) {
            return;
        }
        String[] parts = new String(data, 0, offset - 1, StandardCharsets.UTF_8).split("\t", 4);
        if (parts.length != 4) {
            return;
        }
        String jobId = parts[1];
        int fileIndex = intValue(parts[2], -1);
        int chunkIndex = intValue(parts[3], -1);
        RxFile file = active.get(key(jobId, fileIndex));
        boolean ok = file != null && file.write(chunkIndex, data, offset, length - offset);
        if (ok) {
            downloadRate.pause(length - offset, file.downloadBytesPerSecond);
        }
        ack(socket, packet, jobId, fileIndex, chunkIndex, ok);
    }

    // 读取当前下载限速字节数
    private long downloadBytesPerSecond() {
        int limit = settings.load().downloadLimit();
        return limit <= 0 ? 0 : limit * 1024L * 1024L;
    }

    // 创建单个文件的接收状态
    private RxFile createFile(String fileName, long size, int chunkCount, int chunkSize, String sha256,
                              boolean folderZip) throws IOException {
        if (size < 0 || chunkCount < 0 || chunkSize <= 0) {
            throw new IOException("bad file metadata");
        }
        Path dir = Path.of(settings.load().receiveDir());
        Files.createDirectories(dir);
        Path target = uniqueTarget(dir, safeName(fileName));
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Path meta = target.resolveSibling(target.getFileName() + ".part.meta");
        return new RxFile(target, part, meta, size, chunkCount, chunkSize, sha256, safeName(fileName), folderZip, progress,
                downloadBytesPerSecond());
    }

    // 返回不覆盖旧文件的接收路径
    private Path uniqueTarget(Path dir, String fileName) {
        Path target = dir.resolve(fileName);
        if (!Files.exists(target) && reserved.add(target)) {
            return target;
        }
        int dot = fileName.lastIndexOf('.');
        String name = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        for (int index = 1; ; index++) {
            Path candidate = dir.resolve(name + "-" + index + ext);
            if (!Files.exists(candidate) && reserved.add(candidate)) {
                return candidate;
            }
        }
    }

    // 发送接收确认包
    private void ack(DatagramSocket socket, DatagramPacket packet, String jobId, int fileIndex, int chunkIndex, boolean ok) {
        ack(socket, packet, jobId, fileIndex, chunkIndex, ok, "");
    }

    // 发送带扩展信息的接收确认包
    private void ack(DatagramSocket socket, DatagramPacket packet, String jobId, int fileIndex, int chunkIndex, boolean ok, String detail) {
        try {
            String message = ACK + "\t" + jobId + "\t" + fileIndex + "\t" + chunkIndex + "\t" + (ok ? "OK" : "FAIL")
                    + (detail == null || detail.isBlank() ? "" : "\t" + detail);
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort()));
        } catch (Exception ignored) {
        }
    }

    // 判断数据包是否以指定协议头开头
    private boolean startsWith(byte[] data, int length, String prefix) {
        byte[] head = (prefix + "\t").getBytes(StandardCharsets.UTF_8);
        if (length < head.length) {
            return false;
        }
        for (int i = 0; i < head.length; i++) {
            if (data[i] != head[i]) {
                return false;
            }
        }
        return true;
    }

    // 找到二进制数据包头部结束位置
    private int dataOffset(byte[] data, int length, int tabs) {
        int count = 0;
        for (int i = 0; i < length; i++) {
            if (data[i] == '\t') {
                count++;
                if (count == tabs) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    // 生成传输任务和文件序号的组合键
    private String key(String jobId, int fileIndex) {
        return jobId + ":" + fileIndex;
    }

    // 解码文件名
    private String decodeName(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "received-file";
        }
    }

    // 清理文件名中的非法字符
    private String safeName(String value) {
        String name = value == null ? "" : value.replace('\\', '_').replace('/', '_').replace(':', '_')
                .replace('*', '_').replace('?', '_').replace('"', '_').replace('<', '_').replace('>', '_')
                .replace('|', '_').trim();
        return name.isBlank() ? "received-file" : name;
    }

    // 安全解析整型数值
    private int intValue(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // 解析长整数
    private long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // 单个接收中文件的落盘状态
    private static final class RxFile {
        private final Path target;
        private final Path part;
        private final Path meta;
        private final long size;
        private final int chunkCount;
        private final int chunkSize;
        private final String sha256;
        private final String fileName;
        private final boolean folderZip;
        private final RxProgress progress;
        private final long downloadBytesPerSecond;
        private final FileChannel channel;
        private final BitSet received = new BitSet();
        private int nextProgress = 25;
        private int receivedCount;
        private boolean done;

        // 初始化接收中文件状态
        private RxFile(Path target, Path part, Path meta, long size, int chunkCount, int chunkSize, String sha256,
                       String fileName, boolean folderZip, RxProgress progress, long downloadBytesPerSecond)
                throws IOException {
            this.target = target;
            this.part = part;
            this.meta = meta;
            this.size = size;
            this.chunkCount = chunkCount;
            this.chunkSize = chunkSize;
            this.sha256 = sha256 == null ? "" : sha256;
            this.fileName = fileName;
            this.folderZip = folderZip;
            this.progress = progress;
            this.downloadBytesPerSecond = downloadBytesPerSecond;
            restoreOrReset();
            this.channel = FileChannel.open(part, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }

        // 写入一个文件分片
        private synchronized boolean write(int chunkIndex, byte[] data, int offset, int length) {
            // 分片校验
            if (done) {
                return true;
            }
            if (chunkIndex < 0 || chunkIndex >= chunkCount || length < 0) {
                return false;
            }
            if (received.get(chunkIndex)) {
                return receivedCount < chunkCount || finishOk();
            }
            try {
                channel.write(ByteBuffer.wrap(data, offset, length), (long) chunkIndex * chunkSize);
            } catch (Exception ignored) {
                return false;
            }
            // 分片状态
            if (done) {
                return true;
            }
            if (received.get(chunkIndex)) {
                return true;
            }
            received.set(chunkIndex);
            receivedCount++;
            try {
                if (shouldSaveMeta()) {
                    saveMeta();
                }
            } catch (Exception ignored) {
                return false;
            }
            if (receivedCount == chunkCount) {
                boolean finished = finishOk();
                if (finished) {
                    publish(100);
                }
                return finished;
            }
            publishProgress();
            return true;
        }

        // 判断是否需要保存断点元数据
        private boolean shouldSaveMeta() {
            return receivedCount == 1 || receivedCount % 16 == 0 || receivedCount == chunkCount;
        }

        // 按进度节点推送接收进度
        private void publishProgress() {
            int percent = chunkCount <= 0 ? 100 : Math.min(100, receivedCount * 100 / chunkCount);
            if (percent < nextProgress) {
                return;
            }
            publish(percent);
            while (nextProgress <= percent) {
                nextProgress += 25;
            }
        }

        // 调用接收进度回调
        private void publish(int percent) {
            try {
                progress.update(fileName, percent);
            } catch (Exception ignored) {
            }
        }

        // 完成文件接收并把校验失败转成ACK失败
        private boolean finishOk() {
            try {
                finish();
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }

        // 把临时文件移动为最终接收文件
        private synchronized void finish() throws IOException {
            if (done) {
                return;
            }
            if (size == 0 && !Files.exists(part)) {
                Files.write(part, new byte[0]);
            }
            channel.close();
            if (!sha256.isBlank() && !sha256.equalsIgnoreCase(sha256(part))) {
                throw new IOException("sha256 mismatch");
            }
            Files.move(part, target);
            if (folderZip) {
                unzipTarget();
            }
            Files.deleteIfExists(meta);
            done = true;
        }

        // 解压文件夹传输压缩包
        private void unzipTarget() throws IOException {
            Path dir = unzipDir(target);
            Files.createDirectories(dir);
            try (ZipInputStream input = new ZipInputStream(Files.newInputStream(target))) {
                for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                    unzipEntry(input, entry, dir);
                    input.closeEntry();
                }
            }
            Files.deleteIfExists(target);
        }

        // 生成不覆盖旧目录的解压目录
        private Path unzipDir(Path zip) {
            String fileName = zip.getFileName().toString();
            String base = fileName.toLowerCase().endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;
            Path dir = zip.resolveSibling(base);
            if (!Files.exists(dir)) {
                return dir;
            }
            for (int index = 1; ; index++) {
                Path candidate = zip.resolveSibling(base + "-" + index);
                if (!Files.exists(candidate)) {
                    return candidate;
                }
            }
        }

        // 解压单个zip条目并阻止越界路径
        private void unzipEntry(ZipInputStream input, ZipEntry entry, Path dir) throws IOException {
            Path target = dir.resolve(entry.getName()).normalize();
            if (!target.startsWith(dir)) {
                throw new IOException("bad zip entry");
            }
            if (entry.isDirectory()) {
                Files.createDirectories(target);
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(input, target);
        }

        // 从元数据恢复已接收分片，元数据不匹配时清理旧临时文件
        private void restoreOrReset() throws IOException {
            if (!Files.exists(meta)) {
                Files.deleteIfExists(part);
                return;
            }
            Properties props = new Properties();
            try (Reader reader = Files.newBufferedReader(meta, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            if (!String.valueOf(size).equals(props.getProperty("size"))
                    || !String.valueOf(chunkCount).equals(props.getProperty("chunkCount"))
                    || !String.valueOf(chunkSize).equals(props.getProperty("chunkSize"))
                    || !sha256.equals(props.getProperty("sha256", ""))) {
                Files.deleteIfExists(part);
                Files.deleteIfExists(meta);
                return;
            }
            for (String item : props.getProperty("received", "").split(",")) {
                try {
                    int index = Integer.parseInt(item.trim());
                    if (index >= 0 && index < chunkCount) {
                        received.set(index);
                    }
                } catch (Exception ignored) {
                }
            }
            receivedCount = received.cardinality();
        }

        // 保存当前已接收分片元数据
        private void saveMeta() throws IOException {
            Properties props = new Properties();
            props.setProperty("size", String.valueOf(size));
            props.setProperty("chunkCount", String.valueOf(chunkCount));
            props.setProperty("chunkSize", String.valueOf(chunkSize));
            props.setProperty("sha256", sha256);
            props.setProperty("received", received.stream()
                    .collect(StringBuilder::new, (builder, value) -> {
                        if (!builder.isEmpty()) {
                            builder.append(',');
                        }
                        builder.append(value);
                    }, StringBuilder::append).toString());
            try (Writer writer = Files.newBufferedWriter(meta, StandardCharsets.UTF_8)) {
                props.store(writer, "Lantransfer partial file state");
            }
        }

        // 返回当前缺失分片索引列表
        private String missing() {
            if (receivedCount == 0) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (int index = received.nextClearBit(0); index >= 0 && index < chunkCount; index = received.nextClearBit(index + 1)) {
                if (!builder.isEmpty()) {
                    builder.append(',');
                }
                builder.append(index);
            }
            return builder.toString();
        }

        // 计算接收临时文件SHA-256
        private String sha256(Path path) throws IOException {
            try (InputStream input = Files.newInputStream(path)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                    digest.update(buffer, 0, read);
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch (Exception ex) {
                throw new IOException("sha256 failed", ex);
            }
        }
    }

    // 下载限速器，通过推迟ACK控制发送端速度
    private static final class RateLimit {
        private final long started = System.nanoTime();
        private long received;

        // 按已接收字节数等待到目标速率
        private synchronized void pause(int bytes, long bytesPerSecond) {
            if (bytesPerSecond <= 0 || bytes <= 0) {
                return;
            }
            received += bytes;
            long expected = (long) (received * 1_000_000_000.0 / bytesPerSecond);
            long wait = expected - (System.nanoTime() - started);
            // 亚毫秒等待
            if (wait < 1_000_000) {
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
