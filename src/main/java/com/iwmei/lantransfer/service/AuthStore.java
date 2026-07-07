package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.AuthResult;
import com.iwmei.lantransfer.model.LoginRequest;
import com.iwmei.lantransfer.model.Profile;
import com.iwmei.lantransfer.model.RegisterRequest;
import com.iwmei.lantransfer.model.UserStatus;

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
    private String currentAccount;

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
        String password = request.password();
        if (account.isBlank() || password == null || password.isBlank()) {
            return fail("请输入账号和密码");
        }
        Properties props = load();
        ensureAdmin(props);
        if (!props.containsKey(key(account, "hash"))) {
            return fail("账号不存在，请先注册");
        }
        if (!verify(password, props.getProperty(key(account, "salt")), props.getProperty(key(account, "hash")))) {
            return fail("账号或密码错误");
        }
        LocalDateTime now = LocalDateTime.now();
        props.setProperty(key(account, "lastLoginAt"), TIME.format(now));
        remember(props, account, request.rememberMe());
        save(props);
        currentAccount = account;
        return new AuthResult(true, false, "登录成功", profile(props, account));
    }

    // 读取已记住的最近登录账号
    synchronized String rememberedAccount() {
        Properties props = load();
        return Boolean.parseBoolean(props.getProperty("login.rememberMe"))
                ? props.getProperty("login.account", "")
                : "";
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

    // 更新当前账号资料
    synchronized void updateProfile(Profile profile) {
        if (profile == null) {
            return;
        }
        Properties props = load();
        ensureAdmin(props);
        String account = findByUserId(props, profile.userId());
        if (account == null) {
            return;
        }
        props.setProperty(key(account, "nickname"), cleanText(profile.nickname(), account));
        props.setProperty(key(account, "deviceName"), cleanText(profile.deviceName(), localDeviceName()));
        props.setProperty(key(account, "signature"), cleanText(profile.signature(), "在线，已连接"));
        props.setProperty(key(account, "language"), cleanText(profile.language(), "简体中文"));
        props.setProperty(key(account, "status"), profile.status() == null ? UserStatus.DEFAULT.name() : profile.status().name());
        save(props);
    }

    // 更新当前账号状态和自定义状态文本
    synchronized void updateStatus(UserStatus status, String customText) {
        String account = currentAccount;
        if (account == null) {
            return;
        }
        Properties props = load();
        ensureAdmin(props);
        props.setProperty(key(account, "status"), status == null ? UserStatus.DEFAULT.name() : status.name());
        props.setProperty(key(account, "signature"), cleanText(customText, statusText(status)));
        save(props);
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
            props.setProperty("repo.origin", AppFiles.repoOrigin());
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

    // 根据登录选择保存或清除最近登录账号
    private void remember(Properties props, String account, boolean enabled) {
        if (enabled) {
            props.setProperty("login.rememberMe", "true");
            props.setProperty("login.account", account);
        } else {
            props.remove("login.rememberMe");
            props.remove("login.account");
        }
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
                props.getProperty(key(account, "language"), "简体中文"),
                status(props.getProperty(key(account, "status")))
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

    // 清洗普通资料文本
    private String cleanText(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.isBlank() ? fallback : cleaned;
    }

    // 根据用户 ID 查找账号名
    private String findByUserId(Properties props, String userId) {
        if (userId == null) {
            return null;
        }
        String suffix = ".userId";
        for (String name : props.stringPropertyNames()) {
            if (name.startsWith("account.") && name.endsWith(suffix) && userId.equals(props.getProperty(name))) {
                return name.substring("account.".length(), name.length() - suffix.length());
            }
        }
        return null;
    }

    // 生成状态默认文案
    private String statusText(UserStatus status) {
        return switch (status == null ? UserStatus.DEFAULT : status) {
            case ONLINE -> "在线，允许接收文件";
            case BUSY -> "忙碌，接收前请确认";
            case INVISIBLE -> "隐身，不参与扫描";
            case OFFLINE -> "离线，暂停传输";
            case DEFAULT -> "在线，已连接";
        };
    }

    // 解析用户状态字段
    private UserStatus status(String value) {
        try {
            return UserStatus.valueOf(value);
        } catch (Exception ignored) {
            return UserStatus.DEFAULT;
        }
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
        return AppFiles.dataDir().resolve("users.properties");
    }

    // 构造失败认证结果
    private AuthResult fail(String message) {
        return new AuthResult(false, false, message, null);
    }
}
