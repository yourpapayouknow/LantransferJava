package com.zjh.lanudp.ui.fx;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MockBackendFacade implements BackendFacade {
    private Profile profile = new Profile(
            "admin",
            "U-10001",
            "当前设备",
            "高速、安全、简单的文件传输工具",
            LocalDateTime.now().minusDays(12),
            LocalDateTime.now(),
            "1.0.0",
            "简体中文"
    );

    @Override
    public CompletableFuture<StartupState> startApplication() {
        return CompletableFuture.completedFuture(new StartupState(100, List.of(
                new StartupStep(1, "加载界面资源", "JavaFX 前端已就绪", StepState.DONE),
                new StartupStep(2, "连接后端服务", "等待真实后端接入", StepState.ACTIVE),
                new StartupStep(3, "准备文件传输", "占位数据用于前端联调", StepState.WAITING)
        )));
    }

    @Override
    public CompletableFuture<AuthResult> login(LoginRequest request) {
        if (request.account().isBlank() || request.password().isBlank()) {
            return CompletableFuture.completedFuture(new AuthResult(false, false, "请输入账号和密码", null));
        }
        return CompletableFuture.completedFuture(new AuthResult(true, false, "登录成功", profile));
    }

    @Override
    public CompletableFuture<AuthResult> register(RegisterRequest request) {
        profile = new Profile(
                request.account().isBlank() ? "新用户" : request.account(),
                "PENDING",
                request.deviceName().isBlank() ? "新设备" : request.deviceName(),
                "等待管理员审核",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "1.0.0",
                "简体中文"
        );
        return CompletableFuture.completedFuture(new AuthResult(true, true, "注册申请已提交", profile));
    }

    @Override
    public CompletableFuture<List<UserDevice>> loadRecentDevices() {
        return CompletableFuture.completedFuture(devices().subList(0, 3));
    }

    @Override
    public CompletableFuture<List<UserDevice>> loadAllDevices() {
        return CompletableFuture.completedFuture(devices());
    }

    @Override
    public CompletableFuture<List<UserDevice>> scanLanDevices() {
        return CompletableFuture.completedFuture(devices());
    }

    @Override
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets) {
        List<TransferTask> tasks = new ArrayList<>();
        for (TransferFile file : files) {
            for (UserDevice target : targets) {
                tasks.add(new TransferTask(file.fileName(), target, 100, file.size(), "12.4 MB/s", "00:03", "已完成", 0));
            }
        }
        return CompletableFuture.completedFuture(new TransferSummary(
                targets.size(),
                tasks.size(),
                0,
                0,
                "00:03",
                List.of("创建传输任务", "等待真实后端接入", "Mock 传输完成"),
                tasks
        ));
    }

    @Override
    public void updateProfile(Profile profile) {
        this.profile = profile;
    }

    @Override
    public void updateStatus(UserStatus status, String customText) {
    }

    @Override
    public void updateSettings(SystemSettings settings) {
    }

    private List<UserDevice> devices() {
        return List.of(
                new UserDevice("D-1001", "张三", "Lab-PC-01", DeviceStatus.ONLINE, "刚刚", "张", "#2f80ed", false),
                new UserDevice("D-1002", "李四", "Notebook-02", DeviceStatus.ONLINE, "2 分钟前", "李", "#27ae60", false),
                new UserDevice("D-1003", "王五", "Dorm-PC", DeviceStatus.OFFLINE, "昨天", "王", "#f2994a", false),
                new UserDevice("D-1004", "赵六", "Phone", DeviceStatus.ONLINE, "刚刚", "赵", "#9b51e0", false)
        );
    }
}
