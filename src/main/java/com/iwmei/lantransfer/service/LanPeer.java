package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.UserDevice;

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

// 局域网设备发现服务，负责 UDP 广播扫描和本机发现响应
final class LanPeer {
    private static final int PORT = 45331;
    static final int TRANSFER_PORT = 45332;
    private static final int WAIT_MILLIS = 900;
    private static final String DISCOVER = "LANTRANSFER_DISCOVER_V1";
    private static final String HERE = "LANTRANSFER_HERE_V1";
    private static final List<String> COLORS = List.of("#4f7bd8", "#35c6ca", "#7a52d8", "#5ebd3e", "#db3dbd");
    private final ConcurrentMap<String, UserDevice> seen = new ConcurrentHashMap<>();
    private final UserDevice self;

    // 初始化并启动后台响应线程
    LanPeer() {
        this(true);
    }

    // 初始化局域网发现服务
    LanPeer(boolean startResponder) {
        self = localDevice();
        seen.put(self.id(), self);
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
        seen.put(self.id(), self);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(200);
            byte[] data = DISCOVER.getBytes(StandardCharsets.UTF_8);
            for (InetAddress address : broadcastAddresses()) {
                socket.send(new DatagramPacket(data, data.length, address, PORT));
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

    // 把设备编码成 UDP 响应文本
    String encode(UserDevice device) {
        return HERE + "\t" + clean(device.id(), self.id()) + "\t" + clean(device.nickname(), "用户")
                + "\t" + clean(device.deviceName(), "LOCAL-PC") + "\t" + clean(device.host(), localIp()) + "\t" + device.port();
    }

    // 解析 UDP 响应文本
    UserDevice parse(String message) {
        return parse(message, "");
    }

    // 解析 UDP 响应文本并用来源地址兜底
    private UserDevice parse(String message, String fallbackHost) {
        String[] parts = message == null ? new String[0] : message.split("\t", 6);
        if (parts.length < 4 || !HERE.equals(parts[0])) {
            return null;
        }
        String id = clean(parts[1], idFor(parts[2] + parts[3]));
        String nickname = clean(parts[2], "用户");
        String deviceName = clean(parts[3], "LOCAL-PC");
        String host = parts.length >= 5 ? clean(parts[4], fallbackHost) : fallbackHost;
        int port = parts.length >= 6 ? port(parts[5]) : TRANSFER_PORT;
        return new UserDevice(id, nickname, deviceName, DeviceStatus.ONLINE, "刚刚", initial(nickname), color(id), false, host, port);
    }

    // 接收一次扫描响应
    private void receiveReply(DatagramSocket socket) {
        try {
            byte[] buffer = new byte[512];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            UserDevice device = parse(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8), packet.getAddress().getHostAddress());
            if (device != null) {
                seen.put(device.id(), device);
            }
        } catch (SocketTimeoutException ignored) {
        } catch (Exception ignored) {
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
                byte[] buffer = new byte[512];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                if (DISCOVER.equals(message)) {
                    byte[] data = encode(self).getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort()));
                } else {
                    UserDevice device = parse(message, packet.getAddress().getHostAddress());
                    if (device != null) {
                        seen.put(device.id(), device);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // 获取可用广播地址
    private List<InetAddress> broadcastAddresses() throws Exception {
        Set<InetAddress> addresses = new LinkedHashSet<>();
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
        List<UserDevice> devices = new ArrayList<>(seen.values());
        devices.sort(Comparator.comparing(UserDevice::nickname).thenComparing(UserDevice::deviceName));
        return devices;
    }

    // 构造本机设备信息
    private UserDevice localDevice() {
        String deviceName = localHostName();
        String nickname = clean(System.getProperty("user.name"), "本机");
        String id = idFor(nickname + "@" + deviceName);
        return new UserDevice(id, nickname, deviceName, DeviceStatus.ONLINE, "本机", initial(nickname), color(id), false, localIp(), TRANSFER_PORT);
    }

    // 获取本机主机名
    private String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "LOCAL-PC";
        }
    }

    // 获取本机可传输 IP
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

    // 解析端口
    private int port(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            return port > 0 ? port : TRANSFER_PORT;
        } catch (Exception ex) {
            return TRANSFER_PORT;
        }
    }

    // 生成稳定设备 ID
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

    // 取头像首字
    private String initial(String name) {
        return name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    // 根据 ID 选择头像颜色
    private String color(String id) {
        return COLORS.get(Math.floorMod(id.hashCode(), COLORS.size()));
    }
}
