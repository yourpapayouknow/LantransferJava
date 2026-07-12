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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
final class AuthStore {
    private static final String VERSION = "极速互传 v1.0.0";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String[] COLUMNS = {
            "account", "salt", "hash", "userId", "nickname", "deviceName", "signature",
            "registeredAt", "lastLoginAt", "language", "status", "reviewStatus",
            "reviewRequestedAt", "reviewApprovedAt", "reviewApprover", "avatar"
    };
    private final Path table;
    private final Path local;
    private final Path reqDir;
    private final boolean gitSync;
    private String currentAccount;
    AuthStore() {
        this(defaultTable(), defaultLocal(), defaultReqDir(), true);
    }
    AuthStore(Path table) {
        this(table, table.resolveSibling("la"), table.resolveSibling("req"), false);
    }
    AuthStore(Path table, Path local, Path reqDir, boolean gitSync) {
        this.table = table;
        this.local = local;
        this.reqDir = reqDir;
        this.gitSync = gitSync;
    }
    synchronized AuthResult login(LoginRequest request) {
        try {
            String account = normalize(request.account());
            String password = request.password();
            if (account.isBlank() || password == null || password.isBlank()) {
                return fail("请输入账号和密码");
            }
            Properties accounts = loadAccounts();
            if (!accounts.containsKey(key(account, "hash"))) {
                return fail("账号不存在，请先注册");
            }
            if (!verify(password, accounts.getProperty(key(account, "salt")), accounts.getProperty(key(account, "hash")))) {
                return fail("账号或密码错误");
            }
            accounts.setProperty(key(account, "lastLoginAt"), TIME.format(LocalDateTime.now()));
            Properties remembered = loadLocal();
            remember(remembered, account, request.rememberMe());
            saveLocal(remembered);
            currentAccount = account;
            return new AuthResult(true, false, "登录成功", profile(accounts, account));
        } catch (IllegalStateException ex) {
            return fail(ex.getMessage());
        }
    }
    synchronized String rememberedAccount() {
        Properties props = loadLocal();
        return Boolean.parseBoolean(props.getProperty("login.rememberMe"))
                ? props.getProperty("login.account", "")
                : "";
    }
    synchronized AuthResult register(RegisterRequest request) {
        try {
            String account = normalize(request.account());
            String validation = validate(account, request.password());
            if (validation != null) {
                return fail(validation);
            }
            Properties accounts = loadAccounts();
            if (accounts.containsKey(key(account, "hash"))) {
                return fail("账号已存在，请直接登录");
            }
            LocalDateTime now = LocalDateTime.now();
            putAccount(accounts, account, request.password(), cleanDeviceName(request.deviceName()), now, now);
            approveRegistration(accounts, account, now);
            if (gitSync) {
                saveReq(accounts, account);
                pushPath(reqPath(account), "acco req");
                return waitForAction(account, accounts);
            }
            saveAccounts(accounts);
            return new AuthResult(true, false, "注册已写入账号表，请登录", profile(accounts, account));
        } catch (IllegalStateException ex) {
            return fail(ex.getMessage());
        }
    }
    synchronized void updateProfile(Profile profile) {
        if (profile == null) {
            return;
        }
        try {
            Properties accounts = loadAccounts();
            String account = findByUserId(accounts, profile.userId());
            if (account == null) {
                return;
            }
            accounts.setProperty(key(account, "nickname"), cleanText(profile.nickname(), account));
            accounts.setProperty(key(account, "deviceName"), cleanText(profile.deviceName(), localDeviceName()));
            accounts.setProperty(key(account, "signature"), cleanText(profile.signature(), "在线，已连接"));
            accounts.setProperty(key(account, "language"), cleanText(profile.language(), "简体中文"));
            accounts.setProperty(key(account, "status"), profile.status() == null ? UserStatus.DEFAULT.name() : profile.status().name());
            accounts.setProperty(key(account, "avatar"), cleanText(profile.avatar(), ""));
            saveAccounts(accounts);
            pushPath(table, "acco");
        } catch (IllegalStateException ignored) {
        }
    }
    synchronized void updateStatus(UserStatus status, String customText) {
        String account = currentAccount;
        if (account == null) {
            return;
        }
        try {
            Properties accounts = loadAccounts();
            accounts.setProperty(key(account, "status"), status == null ? UserStatus.DEFAULT.name() : status.name());
            accounts.setProperty(key(account, "signature"), cleanText(customText, statusText(status)));
            saveAccounts(accounts);
            pushPath(table, "acco");
        } catch (IllegalStateException ignored) {
        }
    }
    private AuthResult waitForAction(String account, Properties pending) {
        for (int i = 0; i < 9; i++) {
            sleep(5);
            Properties accounts = loadAccounts();
            if (accounts.containsKey(key(account, "hash"))) {
                return new AuthResult(true, false, "注册已由 GitHub Actions 自动通过，请登录", profile(accounts, account));
            }
        }
        return new AuthResult(true, true, "GitHub Actions 正在自动写入账号表，请稍后登录", profile(pending, account));
    }
    private Properties loadAccounts() {
        pullAccounts();
        return readAccounts();
    }
    private Properties readAccounts() {
        Properties props = new Properties();
        if (!Files.exists(table)) {
            return props;
        }
        try {
            List<String> lines = Files.readAllLines(table, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.split(",", -1);
                String account = normalize(cell(cells, 0));
                if (account.isBlank()) {
                    continue;
                }
                for (int col = 1; col < COLUMNS.length; col++) {
                    props.setProperty(key(account, COLUMNS[col]), cell(cells, col));
                }
            }
            return props;
        } catch (IOException ex) {
            throw new IllegalStateException("读取账号表失败：" + table, ex);
        }
    }
    private void saveAccounts(Properties props) {
        try {
            Path parent = table.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            lines.add(String.join(",", COLUMNS));
            for (String account : accounts(props)) {
                lines.add(row(props, account));
            }
            Files.write(table, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("保存账号表失败：" + table, ex);
        }
    }
    private void saveReq(Properties props, String account) {
        try {
            Files.createDirectories(reqDir);
            Files.writeString(reqPath(account), row(props, account) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("保存注册请求失败：" + reqPath(account), ex);
        }
    }
    private Properties loadLocal() {
        Properties props = new Properties();
        if (!Files.exists(local)) {
            return props;
        }
        try (Reader reader = Files.newBufferedReader(local, StandardCharsets.UTF_8)) {
            props.load(reader);
            return props;
        } catch (IOException ex) {
            throw new IllegalStateException("读取本地登录状态失败：" + local, ex);
        }
    }
    private void saveLocal(Properties props) {
        try {
            Path parent = local.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            props.setProperty("repo.origin", AppFiles.repoOrigin());
            try (Writer writer = Files.newBufferedWriter(local, StandardCharsets.UTF_8)) {
                props.store(writer, "Lantransfer local auth state");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("保存本地登录状态失败：" + local, ex);
        }
    }
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
        props.setProperty(key(account, "status"), UserStatus.DEFAULT.name());
        props.setProperty(key(account, "avatar"), "");
    }
    private void approveRegistration(Properties props, String account, LocalDateTime now) {
        props.setProperty(key(account, "reviewStatus"), "AUTO_APPROVED");
        props.setProperty(key(account, "reviewRequestedAt"), TIME.format(now));
        props.setProperty(key(account, "reviewApprovedAt"), TIME.format(now));
        props.setProperty(key(account, "reviewApprover"), "actions");
    }
    private void remember(Properties props, String account, boolean enabled) {
        if (enabled) {
            props.setProperty("login.rememberMe", "true");
            props.setProperty("login.account", account);
        } else {
            props.remove("login.rememberMe");
            props.remove("login.account");
        }
    }
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
                status(props.getProperty(key(account, "status"))),
                props.getProperty(key(account, "avatar"), "")
        );
    }
    private TreeSet<String> accounts(Properties props) {
        TreeSet<String> accounts = new TreeSet<>();
        String suffix = ".hash";
        for (String name : props.stringPropertyNames()) {
            if (name.startsWith("account.") && name.endsWith(suffix)) {
                accounts.add(name.substring("account.".length(), name.length() - suffix.length()));
            }
        }
        return accounts;
    }
    private String row(Properties props, String account) {
        List<String> cells = new ArrayList<>();
        cells.add(csv(account));
        for (int col = 1; col < COLUMNS.length; col++) {
            cells.add(csv(props.getProperty(key(account, COLUMNS[col]), "")));
        }
        return String.join(",", cells);
    }
    private String csv(String value) {
        return (value == null ? "" : value).replace('\r', ' ').replace('\n', ' ').replace(',', ' ');
    }
    private String cell(String[] cells, int index) {
        return index < cells.length ? cells[index].trim() : "";
    }
    private String key(String account, String field) {
        return "account." + account + "." + field;
    }
    private String normalize(String account) {
        return account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
    }
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
    private boolean verify(String password, String salt, String expectedHash) {
        if (salt == null || salt.isBlank() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(hash(password, salt).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
    private String hash(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), 120_000, 256);
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(encoded);
        } catch (Exception ex) {
            throw new IllegalStateException("生成密码摘要失败", ex);
        }
    }
    private String salt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    private String userId(String account) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8));
            return "U-" + HexFormat.of().formatHex(digest, 0, 4).toUpperCase(Locale.ROOT);
        } catch (Exception ex) {
            throw new IllegalStateException("生成用户 ID 失败", ex);
        }
    }
    private LocalDateTime parseTime(String value) {
        return value == null || value.isBlank() ? LocalDateTime.now() : LocalDateTime.parse(value, TIME);
    }
    private String cleanDeviceName(String deviceName) {
        return deviceName == null || deviceName.isBlank() ? localDeviceName() : csv(deviceName.trim());
    }
    private String cleanText(String value, String fallback) {
        String cleaned = value == null ? "" : csv(value.trim());
        return cleaned.isBlank() ? fallback : cleaned;
    }
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
    private String statusText(UserStatus status) {
        return switch (status == null ? UserStatus.DEFAULT : status) {
            case ONLINE -> "在线，允许接收文件";
            case BUSY -> "忙碌，接收前请确认";
            case INVISIBLE -> "隐身，不参与扫描";
            case OFFLINE -> "离线，暂停传输";
            case DEFAULT -> "在线，已连接";
        };
    }
    private UserStatus status(String value) {
        try {
            return UserStatus.valueOf(value);
        } catch (Exception ignored) {
            return UserStatus.DEFAULT;
        }
    }
    private String localDeviceName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "LOCAL-PC";
        }
    }
    private void pullAccounts() {
        if (!gitSync || !inGitRepo()) {
            return;
        }
        String branch = currentBranch();
        if (branch.isBlank()) {
            return;
        }
        GitResult result = git(30, "pull", "--rebase", "--autostash", "origin", branch);
        if (!result.success()) {
            throw new IllegalStateException("同步账号表失败：" + result.output());
        }
    }
    private void pushPath(Path target, String message) {
        if (!gitSync || !inGitRepo()) {
            return;
        }
        String branch = currentBranch();
        if (branch.isBlank()) {
            throw new IllegalStateException("当前 Git 分支为空，无法推送账号文件");
        }
        String alreadyStaged = git(5, "diff", "--cached", "--name-only").output().trim();
        if (!alreadyStaged.isBlank()) {
            throw new IllegalStateException("Git 暂存区已有其它改动，请先提交或取消暂存后再注册账号");
        }
        String path = relative(target);
        GitResult add = git(10, "add", "--", path);
        if (!add.success()) {
            throw new IllegalStateException("暂存账号文件失败：" + add.output());
        }
        String staged = git(5, "diff", "--cached", "--name-only", "--", path).output().trim();
        if (staged.isBlank()) {
            return;
        }
        ensureGitIdentity();
        GitResult commit = git(20, "commit", "-m", message);
        if (!commit.success()) {
            throw new IllegalStateException("提交账号文件失败：" + commit.output());
        }
        GitResult push = push(branch);
        if (!push.success()) {
            throw new IllegalStateException("推送账号文件失败：" + push.output());
        }
    }
    private void ensureGitIdentity() {
        if (git(5, "config", "user.name").output().isBlank()) {
            git(5, "config", "user.name", "lantransfer");
        }
        if (git(5, "config", "user.email").output().isBlank()) {
            git(5, "config", "user.email", "lantransfer@example.invalid");
        }
    }
    private GitResult push(String branch) {
        String url = pushUrl();
        return url.isBlank()
                ? new GitResult(false, "未配置 ACCO_T 或 -Dacco.t，无法使用辅助账号推送注册请求")
                : git(45, "push", url, "HEAD:" + branch);
    }
    private String pushUrl() {
        String token = token();
        String repo = repoPath(AppFiles.repoOrigin());
        if (token.isBlank() || repo.isBlank()) {
            return "";
        }
        return "https://x-access-token:" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "@github.com/" + repo + ".git";
    }
    private String token() {
        String property = System.getProperty("acco.t", "");
        return property.isBlank() ? System.getenv().getOrDefault("ACCO_T", "") : property;
    }
    private String repoPath(String origin) {
        if (origin == null || origin.isBlank()) {
            return "";
        }
        String value = origin.trim();
        if (value.startsWith("git@github.com:")) {
            value = value.substring("git@github.com:".length());
        } else {
            int marker = value.indexOf("github.com/");
            if (marker < 0) {
                return "";
            }
            value = value.substring(marker + "github.com/".length());
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        if (value.endsWith(".git")) {
            value = value.substring(0, value.length() - 4);
        }
        return value.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") ? value : "";
    }
    private void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
    private boolean inGitRepo() {
        return Files.exists(repoRoot().resolve(".git"));
    }
    private String currentBranch() {
        GitResult result = git(5, "branch", "--show-current");
        return result.success() ? result.output().trim() : "";
    }
    private GitResult git(int timeoutSeconds, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command)
                    .directory(repoRoot().toFile())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new GitResult(false, "git 执行超时");
            }
            String output = clean(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim());
            return new GitResult(process.exitValue() == 0, output);
        } catch (Exception ex) {
            return new GitResult(false, clean(ex.getMessage()));
        }
    }
    private String clean(String output) {
        String token = token();
        if (token.isBlank() || output == null) {
            return output;
        }
        return output.replace(token, "***")
                .replace(URLEncoder.encode(token, StandardCharsets.UTF_8), "***");
    }
    private String relative(Path target) {
        Path root = repoRoot();
        Path full = target.toAbsolutePath().normalize();
        return full.startsWith(root) ? root.relativize(full).toString().replace('\\', '/') : target.toString();
    }
    private Path repoRoot() {
        return Path.of("").toAbsolutePath().normalize();
    }
    private Path reqPath(String account) {
        return reqDir.resolve(account);
    }
    private static Path defaultTable() {
        return Path.of("acco");
    }
    private static Path defaultLocal() {
        return AppFiles.dataDir().resolve("la");
    }
    private static Path defaultReqDir() {
        return Path.of("req");
    }
    private AuthResult fail(String message) {
        return new AuthResult(false, false, message, null);
    }
    private record GitResult(boolean success, String output) {
    }
}
