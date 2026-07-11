package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// 假数据业务实现，供前端联调和课堂展示使用
public final class MockBackendFacade implements BackendFacade {
    private final Profile profile = new Profile("admin", "U-10086", "DESKTOP-8F3K2M1", "在线，已连接",
            LocalDateTime.of(2026, 7, 1, 9, 20),
            LocalDateTime.of(2026, 7, 2, 13, 12),
            "极速互传 v1.0.0", "简体中文", UserStatus.DEFAULT);

    private final List<UserDevice> devices = List.of(
            new UserDevice("lisi", "李四", "DESKTOP-LISI", DeviceStatus.ONLINE, "2 分钟前", "李", "#4f7bd8", false),
            new UserDevice("wangwu", "王五", "LAPTOP-WANGWU", DeviceStatus.ONLINE, "15 分钟前", "王", "#35c6ca", false),
            new UserDevice("zhaoliu", "赵六", "PC-ZHAOLIU", DeviceStatus.OFFLINE, "1 小时前", "赵", "#7a52d8", false),
            new UserDevice("sunqi", "孙七", "DESKTOP-SUNQI", DeviceStatus.ONLINE, "3 小时前", "孙", "#5f86d8", false),
            new UserDevice("zhouba", "周八", "ZHOU-PC", DeviceStatus.OFFLINE, "1 天前", "周", "#db3dbd", false),
            new UserDevice("wujiu", "吴九", "WU-PC", DeviceStatus.OFFLINE, "1 天前", "吴", "#f25c2f", false),
            new UserDevice("zhengshi", "郑十", "ZHENG-PC", DeviceStatus.ONLINE, "12 分钟前", "郑", "#5ebd3e", false),
            new UserDevice("chenshiyi", "陈十一", "CHEN-PC", DeviceStatus.OFFLINE, "昨天 21:18", "陈", "#4386e0", false),
            new UserDevice("heshierr", "何十二", "HE-PC", DeviceStatus.ONLINE, "5 分钟前", "何", "#36b6c4", false),
            new UserDevice("zhushisan", "朱十三", "ZHU-PC", DeviceStatus.OFFLINE, "昨天 20:47", "朱", "#8a52d8", false),
            new UserDevice("qinshisi", "秦十四", "QIN-PC", DeviceStatus.OFFLINE, "昨天 19:35", "秦", "#4f7bd8", false),
            new UserDevice("youshiwu", "尤十五", "YOU-PC", DeviceStatus.OFFLINE, "昨天 18:22", "尤", "#db3dbd", false),
            new UserDevice("xushiliu", "许十六", "XU-PC", DeviceStatus.ONLINE, "8 分钟前", "许", "#4386e0", false),
            new UserDevice("heshiqi", "何十七", "HE17-PC", DeviceStatus.OFFLINE, "3 天前", "何", "#f25c2f", false),
            new UserDevice("lvshiba", "吕十八", "LYU-PC", DeviceStatus.OFFLINE, "2 天前", "吕", "#5ebd3e", false),
            new UserDevice("gaoshijiu", "高十九", "GAO-PC", DeviceStatus.ONLINE, "9 分钟前", "高", "#35c6ca", false),
            new UserDevice("liershi", "李二十", "LI20-PC", DeviceStatus.OFFLINE, "昨天 17:33", "李", "#4f7bd8", false),
            new UserDevice("hanshier", "韩二十一", "HAN21-PC", DeviceStatus.OFFLINE, "昨天 17:09", "韩", "#36b6c4", false)
    );

    // 登录功能的后端调用入口
    @Override
    public CompletableFuture<AuthResult> login(LoginRequest request) {
        boolean matched = "admin".equals(request.account()) && "admin".equals(request.password());
        return CompletableFuture.completedFuture(matched
                ? new AuthResult(true, false, "登录成功", profile)
                : new AuthResult(false, false, "账号或密码错误，请使用 admin / admin", null));
    }

    // 注册功能的后端调用入口
    @Override
    public CompletableFuture<AuthResult> register(RegisterRequest request) {
        return CompletableFuture.completedFuture(new AuthResult(true, true, "注册申请已提交", profile));
    }

    // 加载本地记住的最近登录账号
    @Override
    public CompletableFuture<String> loadRememberedAccount() {
        return CompletableFuture.completedFuture("admin");
    }

    // 加载近期传输对象列表
    @Override
    public CompletableFuture<List<UserDevice>> loadRecentDevices() {
        return CompletableFuture.completedFuture(devices.subList(0, 5));
    }

    // 加载全部可传输用户设备
    @Override
    public CompletableFuture<List<UserDevice>> loadAllDevices() {
        return CompletableFuture.completedFuture(devices);
    }

    // 加载全部本地传输分组
    @Override
    public CompletableFuture<List<Group>> loadGroups() {
        return CompletableFuture.completedFuture(List.of(new Group("演示组", "demo", devices.subList(0, 3))));
    }

    // 保存本地传输分组并返回组目标
    @Override
    public CompletableFuture<UserDevice> saveGroup(String name, String code, List<UserDevice> members) {
        return CompletableFuture.completedFuture(UserDevice.group(name, members == null ? 0 : members.size()));
    }

    // 扫描局域网用户设备
    @Override
    public CompletableFuture<List<UserDevice>> scanLanDevices() {
        return CompletableFuture.completedFuture(devices.subList(0, 4));
    }

    // 加载系统设置参数
    @Override
    public CompletableFuture<SystemSettings> loadSettings() {
        return CompletableFuture.completedFuture(new SystemSettings("192.168.1.100", "fe80::1a2b:3c4d", 10, 20, 3,
                "#ff8500", "Microsoft YaHei", 14, 100));
    }

    // 启动文件传输任务
    @Override
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets) {
        List<UserDevice> safeTargets = targets.isEmpty() ? devices.subList(0, 5) : targets;
        List<TransferTask> tasks = new ArrayList<>();
        tasks.add(new TransferTask("产品演示视频.mp4", safeTargets.get(0), 72, "512.00 MB", "12.35 MB/s", "00:00:32", "传输中", 0));
        tasks.add(new TransferTask("会议纪要.png", safeTargets.get(Math.min(1, safeTargets.size() - 1)), 36, "3.21 MB", "2.11 MB/s", "00:00:08", "传输中", 0));
        tasks.add(new TransferTask("用户手册.pdf", safeTargets.get(Math.min(2, safeTargets.size() - 1)), 100, "8.34 MB", "-", "2025-05-18 10:42:15", "已完成", 0));
        tasks.add(new TransferTask("数据备份_20250517.zip", safeTargets.get(Math.min(3, safeTargets.size() - 1)), 0, "1024.00 MB", "-", "2025-05-18 09:15:33", "传输失败", 3));
        List<String> logs = List.of(
                "[10:41:02.231]  任务开始：共 5 个目标，文件总数 4 个，大小 283.34 MB",
                "[10:41:02.512]  连接已建立，开始发送文件",
                "[10:41:15.021]  ✓ [李四(DESKTOP-LISI)] 发送成功 (4/4)  耗时 00:00:12",
                "[10:41:33.785]  ⚠ [赵六(PC-ZHAOLIU)] 连接超时，尝试重试 (1/3)",
                "[10:42:16.069]  任务结束"
        );
        return CompletableFuture.completedFuture(new TransferSummary(safeTargets.size(), 4, 1, 2, "00:01:28", logs, tasks));
    }

    // 更新用户资料信息
    @Override
    public void updateProfile(Profile profile) {
    }

    // 更新用户在线状态
    @Override
    public void updateStatus(UserStatus status, String customText) {
    }

    // 更新系统设置参数
    @Override
    public void updateSettings(SystemSettings settings) {
    }
}
