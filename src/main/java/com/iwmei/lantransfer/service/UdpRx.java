package com.iwmei.lantransfer.service;

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

// UDP 文件接收服务，负责后台监听传输端口并把收到的文件落盘到接收目录
final class UdpRx {
    static final String BEGIN = "LANTRANSFER_FILE_BEGIN_V1";
    static final String DATA = "LANTRANSFER_FILE_DATA_V1";
    static final String ACK = "LANTRANSFER_FILE_ACK_V1";
    private static final int BUFFER_BYTES = 60_000;

    private final SettingsStore settings;
    private final int port;
    private final Map<String, RxFile> active = new ConcurrentHashMap<>();
    private final Set<Path> reserved = ConcurrentHashMap.newKeySet();
    private volatile boolean running;

    // 使用默认传输端口初始化接收服务
    UdpRx(SettingsStore settings) {
        this(settings, LanPeer.TRANSFER_PORT);
    }

    // 使用指定端口初始化接收服务，供测试复用
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

    // 持续监听 UDP 文件传输数据包
    private void listen() {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            while (running) {
                byte[] buffer = new byte[BUFFER_BYTES];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handle(socket, packet);
            }
        } catch (Exception ignored) {
        }
    }

    // 按协议类型处理接收到的数据包
    private void handle(DatagramSocket socket, DatagramPacket packet) {
        byte[] data = packet.getData();
        int length = packet.getLength();
        if (startsWith(data, length, BEGIN)) {
            handleBegin(socket, packet);
        } else if (startsWith(data, length, DATA)) {
            handleData(socket, packet);
        }
    }

    // 处理文件开始包并创建接收状态
    private void handleBegin(DatagramSocket socket, DatagramPacket packet) {
        String[] parts = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).split("\t", 8);
        if (parts.length < 7) {
            return;
        }
        String jobId = parts[1];
        int fileIndex = intValue(parts[2], -1);
        String key = key(jobId, fileIndex);
        boolean ok = false;
        if (active.containsKey(key)) {
            ack(socket, packet, jobId, fileIndex, -1, true);
            return;
        }
        try {
            String fileName = decodeName(parts[3]);
            long size = longValue(parts[4], -1);
            int chunkCount = intValue(parts[5], -1);
            int chunkSize = intValue(parts[6], -1);
            String sha256 = parts.length >= 8 ? parts[7] : "";
            RxFile file = createFile(fileName, size, chunkCount, chunkSize, sha256);
            active.put(key, file);
            if (chunkCount == 0) {
                file.finish();
            }
            ok = true;
        } catch (Exception ignored) {
        }
        ack(socket, packet, jobId, fileIndex, -1, ok);
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
        ack(socket, packet, jobId, fileIndex, chunkIndex, ok);
    }

    // 创建单个文件的接收状态
    private RxFile createFile(String fileName, long size, int chunkCount, int chunkSize, String sha256) throws IOException {
        if (size < 0 || chunkCount < 0 || chunkSize <= 0) {
            throw new IOException("bad file metadata");
        }
        Path dir = Path.of(settings.load().receiveDir());
        Files.createDirectories(dir);
        Path target = uniqueTarget(dir, safeName(fileName));
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Path meta = target.resolveSibling(target.getFileName() + ".part.meta");
        return new RxFile(target, part, meta, size, chunkCount, chunkSize, sha256);
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
        try {
            String message = ACK + "\t" + jobId + "\t" + fileIndex + "\t" + chunkIndex + "\t" + (ok ? "OK" : "FAIL");
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

    // 解析整数
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
        private final BitSet received = new BitSet();
        private int receivedCount;
        private boolean done;

        // 初始化接收中文件状态
        private RxFile(Path target, Path part, Path meta, long size, int chunkCount, int chunkSize, String sha256) throws IOException {
            this.target = target;
            this.part = part;
            this.meta = meta;
            this.size = size;
            this.chunkCount = chunkCount;
            this.chunkSize = chunkSize;
            this.sha256 = sha256 == null ? "" : sha256;
            restoreOrReset();
        }

        // 写入一个文件分片
        private synchronized boolean write(int chunkIndex, byte[] data, int offset, int length) {
            if (done) {
                return true;
            }
            if (chunkIndex < 0 || chunkIndex >= chunkCount || length < 0) {
                return false;
            }
            if (received.get(chunkIndex)) {
                return receivedCount < chunkCount || finishOk();
            }
            try (FileChannel channel = FileChannel.open(part, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                channel.position((long) chunkIndex * chunkSize);
                channel.write(ByteBuffer.wrap(data, offset, length));
                received.set(chunkIndex);
                receivedCount++;
                saveMeta();
                if (receivedCount == chunkCount) {
                    return finishOk();
                }
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }

        // 完成文件接收并把校验失败转成 ACK 失败
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
            if (!sha256.isBlank() && !sha256.equalsIgnoreCase(sha256(part))) {
                throw new IOException("sha256 mismatch");
            }
            Files.move(part, target);
            Files.deleteIfExists(meta);
            done = true;
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

        // 计算接收临时文件 SHA-256
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
}
