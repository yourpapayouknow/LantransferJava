package com.iwmei.lantransfer.service;
import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.Profile;
import com.iwmei.lantransfer.model.UserDevice;
import com.iwmei.lantransfer.model.UserStatus;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// 局域网设备发现服务，负责UDP广播扫描和本机发现响应
final class LanPeer {
    private static final int PORT = configuredPort("lantransfer.discoveryPort", "LANTRANSFER_DISCOVERY_PORT", 45331);
    static final int TRANSFER_PORT = configuredPort("lantransfer.transferPort", "LANTRANSFER_TRANSFER_PORT", 45332);
    private static final List<Integer> SCAN_PORTS = configuredScanPorts();
    private static final int WAIT_MILLIS = 900;
    private static final long OFFLINE_MILLIS = 30_000;
    private static final int PACKET_BYTES = 60_000;
    private static final String DISCOVER = "LANTRANSFER_DISCOVER_V1";
    private static final String HERE = "LANTRANSFER_HERE_V1";
    private static final List<String> COLORS = List.of("#4f7bd8", "#35c6ca", "#7a52d8", "#5ebd3e", "#db3dbd");
    private final ConcurrentMap<String, UserDevice> seen = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> seenAt = new ConcurrentHashMap<>();
    private final long offlineMillis;
    private volatile UserDevice self;
    private volatile String groupHash = "";

    // 初始化并启动后台响应线程
    LanPeer() {
        this(true);
    }

    // 初始化局域网发现服务
    LanPeer(boolean startResponder) {
        this(startResponder, OFFLINE_MILLIS);
    }

    // 初始化局域网发现服务并指定离线阈值
    LanPeer(boolean startResponder, long offlineMillis) {
        this.offlineMillis = Math.max(1, offlineMillis);
        self = localDevice();
        remember(self);
        if (startResponder) {
            startResponder();
        }
    }

    // 返回已发现设备
    List<UserDevice> knownDevices() {
        return sorted();
    }

    // 广播扫描局域网设备
    List<UserDevice> scan() {
        remember(self);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(200);
            byte[] data = discoverMessage().getBytes(StandardCharsets.UTF_8);
            for (InetAddress address : broadcastAddresses()) {
                for (int port : SCAN_PORTS) {
                    sendDiscover(socket, data, address, port);
                }
            }
            long deadline = System.currentTimeMillis() + WAIT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                receiveReply(socket);
            }
        } catch (Exception ignored) {
            return sorted();
        }
        return sorted();
    }

    // 向单个地址端口发送发现包，失败时不影响其他地址和接收阶段
    private void sendDiscover(DatagramSocket socket, byte[] data, InetAddress address, int port) {
        try {
            socket.send(new DatagramPacket(data, data.length, address, port));
        } catch (Exception ignored) {
        }
    }

    // 把设备编码成UDP响应文本
    String encode(UserDevice device) {
        return HERE + "\t" + clean(device.id(), self.id()) + "\t" + clean(device.nickname(), "用户")
                + "\t" + clean(device.deviceName(), "LOCAL-PC") + "\t" + clean(device.host(), localIp()) + "\t" + device.port()
                + "\t" + userStatus(device.userStatus()).name() + "\t" + groupHash
                + "\t" + clean(device.signature(), "") + "\t" + avatar(device.avatar());
    }

    // 解析UDP响应文本
    UserDevice parse(String message) {
        return parse(message, "");
    }

    // 解析UDP响应文本并用来源地址兜底
    private UserDevice parse(String message, String fallbackHost) {
        String[] parts = message == null ? new String[0] : message.split("\t", 10);
        if (parts.length < 4 || !HERE.equals(parts[0])) {
            return null;
        }
        if (!groupMatches(parts.length >= 8 ? parts[7] : "")) {
            return null;
        }
        String id = clean(parts[1], idFor(parts[2] + parts[3]));
        String nickname = clean(parts[2], "用户");
        String deviceName = clean(parts[3], "LOCAL-PC");
        String host = parts.length >= 5 ? clean(parts[4], fallbackHost) : fallbackHost;
        int port = parts.length >= 6 ? port(parts[5]) : TRANSFER_PORT;
        UserStatus status = parts.length >= 7 ? userStatus(parts[6]) : UserStatus.DEFAULT;
        String signature = parts.length >= 9 ? clean(parts[8], "") : "";
        String avatar = parts.length >= 10 ? avatar(parts[9]) : "";
        return new UserDevice(id, nickname, deviceName, DeviceStatus.ONLINE, "刚刚", initial(nickname), color(id),
                !avatar.isBlank(), host, port, status, signature, avatar);
    }

    // 接收一次扫描响应
    private void receiveReply(DatagramSocket socket) {
        try {
            byte[] buffer = new byte[PACKET_BYTES];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            UserDevice device = parse(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8), packet.getAddress().getHostAddress());
            if (device != null) {
                remember(device);
            }
        } catch (SocketTimeoutException ignored) {
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // 启动后台响应线程
    private void startResponder() {
        Thread thread = new Thread(this::replyLoop, "lantransfer-lan-peer");
        thread.setDaemon(true);
        thread.start();
    }

    // 监听发现请求并回复本机信息
    private void replyLoop() {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(PORT));
            while (true) {
                byte[] buffer = new byte[PACKET_BYTES];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                if (isDiscover(message)) {
                    if (!groupMatches(discoverGroup(message))) {
                        continue;
                    }
                    if (!discoverable(self.userStatus())) {
                        continue;
                    }
                    byte[] data = encode(self).getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort()));
                } else {
                    UserDevice device = parse(message, packet.getAddress().getHostAddress());
                    if (device != null) {
                        remember(device);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // 获取可用广播地址
    private List<InetAddress> broadcastAddresses() throws Exception {
        Set<InetAddress> addresses = new LinkedHashSet<>();
        addresses.add(InetAddress.getByName("127.0.0.1"));
        addresses.add(InetAddress.getByName("255.255.255.255"));
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface item = interfaces.nextElement();
            if (!item.isUp() || item.isLoopback() || item.isVirtual()) {
                continue;
            }
            for (InterfaceAddress address : item.getInterfaceAddresses()) {
                if (address.getBroadcast() != null) {
                    addresses.add(address.getBroadcast());
                }
            }
        }
        return new ArrayList<>(addresses);
    }

    // 返回排序后的设备列表
    private List<UserDevice> sorted() {
        remember(self);
        List<UserDevice> devices = new ArrayList<>();
        for (UserDevice device : seen.values()) {
            devices.add(withStatus(device));
        }
        devices.sort(Comparator.comparing(UserDevice::nickname).thenComparing(UserDevice::deviceName));
        return devices;
    }

    // 记录一次设备发现时间
    void remember(UserDevice device) {
        if (device == null || device.id().isBlank()) {
            return;
        }
        seen.put(device.id(), device);
        seenAt.put(device.id(), System.currentTimeMillis());
    }

    // 按当前登录资料刷新本机发现信息
    void updateSelf(Profile profile) {
        if (profile == null) {
            return;
        }
        UserDevice current = self;
        seen.remove(current.id());
        seenAt.remove(current.id());
        String nickname = clean(profile.nickname(), current.nickname());
        String account = clean(profile.userId(), current.id());
        String id = idFor(account + "@" + current.host() + ":" + current.port());
        self = new UserDevice(id, nickname, clean(profile.deviceName(), current.deviceName()), DeviceStatus.ONLINE, "本机",
                initial(nickname), color(id), !avatar(profile.avatar()).isBlank(), current.host(), current.port(),
                userStatus(profile.status()), clean(profile.signature(), ""), avatar(profile.avatar()));
        remember(self);
        announce();
    }

    // 按当前状态刷新本机发现信息
    void updateStatus(UserStatus status) {
        updateStatus(status, self.signature());
    }

    // 按当前状态和签名刷新本机发现信息
    void updateStatus(UserStatus status, String signature) {
        UserDevice current = self;
        self = new UserDevice(current.id(), current.nickname(), current.deviceName(), DeviceStatus.ONLINE, "本机",
                current.avatarText(), current.color(), current.imageAvatar(), current.host(), current.port(),
                userStatus(status), clean(signature, current.signature()), current.avatar());
        remember(self);
        announce();
    }

    // 更新旧协议分组摘要
    void updateGroup(String groupCode) {
        groupHash = groupHash(groupCode);
    }

    // 根据最后发现时间生成在线或离线展示对象
    private UserDevice withStatus(UserDevice device) {
        long age = System.currentTimeMillis() - seenAt.getOrDefault(device.id(), 0L);
        UserStatus userStatus = userStatus(device.userStatus());
        boolean online = (self.id().equals(device.id()) || age <= offlineMillis) && discoverable(userStatus);
        return new UserDevice(device.id(), device.nickname(), device.deviceName(),
                online ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE, online ? seenText(device, age) : statusText(userStatus),
                device.avatarText(), device.color(), device.imageAvatar(), device.host(), device.port(), userStatus,
                device.signature(), device.avatar());
    }

    // 生成最后发现时间展示文本
    private String seenText(UserDevice device, long age) {
        if (self.id().equals(device.id())) {
            return "本机";
        }
        if (age < 1000) {
            return "刚刚";
        }
        if (age < 60_000) {
            return age / 1000 + " 秒前";
        }
        return age / 60_000 + " 分钟前";
    }

    // 构造本机设备信息
    private UserDevice localDevice() {
        String deviceName = localHostName();
        String nickname = clean(System.getProperty("user.name"), "本机");
        String id = idFor(nickname + "@" + deviceName + ":" + TRANSFER_PORT);
        return new UserDevice(id, nickname, deviceName, DeviceStatus.ONLINE, "本机", initial(nickname), color(id), false, localIp(), TRANSFER_PORT);
    }

    // 静默广播本机资料给局域网客户端
    private void announce() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] data = encode(self).getBytes(StandardCharsets.UTF_8);
            for (InetAddress address : broadcastAddresses()) {
                for (int port : SCAN_PORTS) {
                    sendDiscover(socket, data, address, port);
                }
            }
        } catch (Exception ignored) {
        }
    }

    // 获取本机主机名
    private String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "LOCAL-PC";
        }
    }

    // 获取本机可传输IP
    private String localIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface item = interfaces.nextElement();
                if (!item.isUp() || item.isLoopback() || item.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = item.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') < 0) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            return "127.0.0.1";
        }
        return "127.0.0.1";
    }

    // 安全解析网络端口
    private int port(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            return port > 0 ? port : TRANSFER_PORT;
        } catch (Exception ex) {
            return TRANSFER_PORT;
        }
    }

    // 读取多实例测试或虚拟机联调指定的单个端口
    private static int configuredPort(String propertyName, String envName, int fallback) {
        String value = System.getProperty(propertyName, "").trim();
        if (value.isBlank()) {
            value = System.getenv().getOrDefault(envName, "").trim();
        }
        try {
            int port = Integer.parseInt(value);
            return port > 0 ? port : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // 读取扫描时要探测的发现端口集合
    private static List<Integer> configuredScanPorts() {
        Set<Integer> ports = new LinkedHashSet<>();
        ports.add(PORT);
        String value = System.getProperty("lantransfer.discoveryPorts", "").trim();
        if (value.isBlank()) {
            value = System.getenv().getOrDefault("LANTRANSFER_DISCOVERY_PORTS", "").trim();
        }
        for (String item : value.split("[,;\\s]+")) {
            int port = configuredPortValue(item, -1);
            if (port > 0) {
                ports.add(port);
            }
        }
        return List.copyOf(ports);
    }

    // 解析端口字符串
    private static int configuredPortValue(String value, int fallback) {
        try {
            int port = Integer.parseInt(value.trim());
            return port > 0 ? port : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // 构造发现请求文本
    private String discoverMessage() {
        return groupHash.isBlank() ? DISCOVER : DISCOVER + "\t" + groupHash;
    }

    // 判断文本是否为发现请求
    private boolean isDiscover(String message) {
        return DISCOVER.equals(message) || (message != null && message.startsWith(DISCOVER + "\t"));
    }

    // 解析发现请求中的分组摘要
    private String discoverGroup(String message) {
        return message == null || !message.startsWith(DISCOVER + "\t") ? "" : message.substring((DISCOVER + "\t").length()).trim();
    }

    // 允许局域网发现不再按旧全局口令隔离
    private boolean groupMatches(String remoteHash) {
        return true;
    }

    // 生成口令分组摘要
    private String groupHash(String groupCode) {
        String value = clean(groupCode, "");
        if (value.isBlank()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return "";
        }
    }

    // 解析用户在线状态
    private UserStatus userStatus(String value) {
        try {
            return UserStatus.valueOf(value);
        } catch (Exception ignored) {
            return UserStatus.DEFAULT;
        }
    }

    // 兜底用户在线状态
    private UserStatus userStatus(UserStatus status) {
        return status == null ? UserStatus.DEFAULT : status;
    }

    // 判断用户状态是否允许被发现和直接传输
    private boolean discoverable(UserStatus status) {
        UserStatus value = userStatus(status);
        return value != UserStatus.INVISIBLE && value != UserStatus.OFFLINE;
    }

    // 生成状态展示文本
    private String statusText(UserStatus status) {
        return switch (userStatus(status)) {
            case INVISIBLE -> "对方隐身";
            case OFFLINE -> "对方离线";
            default -> "已离线";
        };
    }

    // 生成稳定设备ID
    private String idFor(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "D-" + HexFormat.of().formatHex(digest, 0, 4).toUpperCase(Locale.ROOT);
        } catch (Exception ex) {
            return "D-LOCAL";
        }
    }

    // 清洗协议字段
    private String clean(String value, String fallback) {
        String cleaned = value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
        if (cleaned.isBlank()) {
            return fallback;
        }
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    // 清洗头像传输字段
    private String avatar(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() > 24_000) {
            return "";
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean ok = ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '+' || ch == '/' || ch == '=';
            if (!ok) {
                return "";
            }
        }
        return text;
    }

    // 取头像首字
    private String initial(String name) {
        return name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    // 根据ID选择头像颜色
    private String color(String id) {
        return COLORS.get(Math.floorMod(id.hashCode(), COLORS.size()));
    }
}
