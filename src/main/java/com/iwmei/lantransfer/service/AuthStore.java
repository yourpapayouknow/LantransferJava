package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.AuthResult;
import com.iwmei.lantransfer.model.LoginRequest;
import com.iwmei.lantransfer.model.Profile;
import com.iwmei.lantransfer.model.RegisterRequest;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

// 本地账号仓库，负责无服务器环境下的注册、登录和密码校验
final class AuthStore {
    private static final String VERSION = "极速互传 v1.0.0";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path store;

    // 使用默认用户目录账号文件初始化仓库
    AuthStore() {
        this(defaultStore());
    }

    // 使用指定账号文件初始化仓库，供测试和后续迁移复用
    AuthStore(Path store) {
        this.store = store;
    }

    // 校验账号密码并返回登录结果
    synchronized AuthResult login(LoginRequest request) {
        String account = normalize(request.account());
        if (account.isBlank() || request.password().isBlank()) {
            return fail("请输入账号和密码");
        }
        Properties props = load();
        ensureAdmin(props);
        if (!props.containsKey(key(account, "hash"))) {
            return fail("账号不存在，请先注册");
        }
        if (!verify(request.password(), props.getProperty(key(account, "salt")), props.getProperty(key(account, "hash")))) {
            return fail("账号或密码错误");
        }
        LocalDateTime now = LocalDateTime.now();
        props.setProperty(key(account, "lastLoginAt"), TIME.format(now));
        save(props);
        return new AuthResult(true, false, "登录成功", profile(props, account));
    }

    // 创建本地账号并返回注册结果
    synchronized AuthResult register(RegisterRequest request) {
        String account = normalize(request.account());
        String validation = validate(account, request.password());
        if (validation != null) {
            return fail(validation);
        }
        Properties props = load();
        ensureAdmin(props);
        if (props.containsKey(key(account, "hash"))) {
            return fail("账号已存在，请直接登录");
        }
        LocalDateTime now = LocalDateTime.now();
        putAccount(props, account, request.password(), cleanDeviceName(request.deviceName()), now, now);
        save(props);
        return new AuthResult(true, false, "注册成功，请登录", profile(props, account));
    }

    // 加载账号文件
    private Properties load() {
        Properties props = new Properties();
        if (!Files.exists(store)) {
            return props;
        }
        try (Reader reader = Files.newBufferedReader(store, StandardCharsets.UTF_8)) {
            props.load(reader);
            return props;
        } catch (IOException ex) {
            throw new IllegalStateException("读取账号文件失败：" + store, ex);
        }
    }

    // 保存账号文件
    private void save(Properties props) {
        try {
            Files.createDirectories(store.getParent());
            props.setProperty("repo.origin", repoOrigin());
            try (Writer writer = Files.newBufferedWriter(store, StandardCharsets.UTF_8)) {
                props.store(writer, "Lantransfer local accounts");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("保存账号文件失败：" + store, ex);
        }
    }

    // 确保演示默认账号可登录
    private void ensureAdmin(Properties props) {
        if (props.containsKey(key("admin", "hash"))) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        putAccount(props, "admin", "admin", localDeviceName(), now, now);
        save(props);
    }

    // 写入单个账号资料和密码摘要
    private void putAccount(Properties props, String account, String password, String deviceName,
                            LocalDateTime registeredAt, LocalDateTime lastLoginAt) {
        String salt = salt();
        props.setProperty(key(account, "salt"), salt);
        props.setProperty(key(account, "hash"), hash(password, salt));
        props.setProperty(key(account, "userId"), userId(account));
        props.setProperty(key(account, "nickname"), account);
        props.setProperty(key(account, "deviceName"), deviceName);
        props.setProperty(key(account, "signature"), "在线，已连接");
        props.setProperty(key(account, "registeredAt"), TIME.format(registeredAt));
        props.setProperty(key(account, "lastLoginAt"), TIME.format(lastLoginAt));
        props.setProperty(key(account, "language"), "简体中文");
    }

    // 从账号属性构造用户资料对象
    private Profile profile(Properties props, String account) {
        return new Profile(
                props.getProperty(key(account, "nickname"), account),
                props.getProperty(key(account, "userId"), userId(account)),
                props.getProperty(key(account, "deviceName"), localDeviceName()),
                props.getProperty(key(account, "signature"), "在线，已连接"),
                parseTime(props.getProperty(key(account, "registeredAt"))),
                parseTime(props.getProperty(key(account, "lastLoginAt"))),
                VERSION,
                props.getProperty(key(account, "language"), "简体中文")
        );
    }

    // 生成账号属性键名
    private String key(String account, String field) {
        return "account." + account + "." + field;
    }

    // 清洗账号输入
    private String normalize(String account) {
        return account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
    }

    // 校验注册输入
    private String validate(String account, String password) {
        if (account.isBlank() || password == null || password.isBlank()) {
            return "请输入账号和密码";
        }
        if (!account.matches("[a-z0-9_.@-]{3,32}")) {
            return "账号只能使用 3-32 位字母、数字、点、下划线、@ 或横线";
        }
        if (password.length() < 4) {
            return "密码至少 4 位";
        }
        return null;
    }

    // 校验密码摘要
    private boolean verify(String password, String salt, String expectedHash) {
        return MessageDigest.isEqual(hash(password, salt).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    // 生成密码摘要
    private String hash(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), 120_000, 256);
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(encoded);
        } catch (Exception ex) {
            throw new IllegalStateException("生成密码摘要失败", ex);
        }
    }

    // 生成密码盐
    private String salt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    // 生成稳定用户 ID
    private String userId(String account) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8));
            return "U-" + HexFormat.of().formatHex(digest, 0, 4).toUpperCase(Locale.ROOT);
        } catch (Exception ex) {
            throw new IllegalStateException("生成用户 ID 失败", ex);
        }
    }

    // 解析账号时间字段
    private LocalDateTime parseTime(String value) {
        return value == null || value.isBlank() ? LocalDateTime.now() : LocalDateTime.parse(value, TIME);
    }

    // 清洗设备名称输入
    private String cleanDeviceName(String deviceName) {
        return deviceName == null || deviceName.isBlank() ? localDeviceName() : deviceName.trim();
    }

    // 获取本机设备名
    private String localDeviceName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "LOCAL-PC";
        }
    }

    // 生成默认账号文件路径
    private static Path defaultStore() {
        return Path.of(System.getProperty("user.home"), ".lantransfer", repoSlug(), "users.properties");
    }

    // 读取 GitHub 远程仓库名作为本地账号命名空间
    private static String repoSlug() {
        String origin = repoOrigin();
        int slash = origin.lastIndexOf('/');
        String name = slash >= 0 ? origin.substring(slash + 1) : origin;
        return name.replace(".git", "").replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    // 读取当前仓库 origin 地址
    private static String repoOrigin() {
        Path config = Path.of(".git", "config");
        if (!Files.exists(config)) {
            return "LantransferJava";
        }
        try {
            boolean inOrigin = false;
            for (String line : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[remote ")) {
                    inOrigin = trimmed.equals("[remote \"origin\"]");
                } else if (inOrigin && trimmed.startsWith("url =")) {
                    return trimmed.substring("url =".length()).trim();
                }
            }
        } catch (IOException ignored) {
            return "LantransferJava";
        }
        return "LantransferJava";
    }

    // 构造失败认证结果
    private AuthResult fail(String message) {
        return new AuthResult(false, false, message, null);
    }
}
