package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// 本地业务后端，负责已落地功能并临时复用演示数据补齐未实现页面
public final class LocalBackend implements BackendFacade {
    private final AuthStore auth = new AuthStore();
    private final MockBackendFacade demo = new MockBackendFacade();
    private final SettingsStore settings = new SettingsStore();
    private final RecentStore recent = new RecentStore();
    private final UdpTx tx = new UdpTx();
    private final UdpRx rx = new UdpRx(settings);
    private final LanPeer lan = new LanPeer();
    private final ExecutorService transferQueue = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "lantransfer-tx-queue");
        thread.setDaemon(true);
        return thread;
    });

    // 初始化本地后端并启动 UDP 接收服务
    public LocalBackend() {
        lan.updateGroup(settings.load().groupCode());
        rx.start();
    }

    // 登录功能的本地后端调用入口
    @Override
    public CompletableFuture<AuthResult> login(LoginRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            AuthResult result = auth.login(request);
            if (result.success() && result.profile() != null) {
                lan.updateSelf(result.profile());
                rx.updateStatus(result.profile().status());
            }
            return result;
        });
    }

    // 注册功能的本地后端调用入口
    @Override
    public CompletableFuture<AuthResult> register(RegisterRequest request) {
        return CompletableFuture.supplyAsync(() -> auth.register(request));
    }

    // 加载本地记住的最近登录账号
    @Override
    public CompletableFuture<String> loadRememberedAccount() {
        return CompletableFuture.supplyAsync(auth::rememberedAccount);
    }

    // 加载近期传输对象列表
    @Override
    public CompletableFuture<List<UserDevice>> loadRecentDevices() {
        return CompletableFuture.supplyAsync(() -> {
            List<UserDevice> devices = recent.load();
            return devices.isEmpty() ? demo.loadRecentDevices().join() : devices;
        });
    }

    // 加载全部可传输用户设备
    @Override
    public CompletableFuture<List<UserDevice>> loadAllDevices() {
        return CompletableFuture.supplyAsync(() -> {
            List<UserDevice> devices = lan.knownDevices();
            return devices.size() > 1 ? devices : demo.loadAllDevices().join();
        });
    }

    // 扫描局域网用户设备
    @Override
    public CompletableFuture<List<UserDevice>> scanLanDevices() {
        return CompletableFuture.supplyAsync(lan::scan);
    }

    // 加载系统设置参数
    @Override
    public CompletableFuture<SystemSettings> loadSettings() {
        return CompletableFuture.supplyAsync(settings::load);
    }

    // 启动文件传输任务
    @Override
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets) {
        return startTransfer(files, targets, summary -> {
        });
    }

    // 启动文件传输任务并推送传输中进度快照
    @Override
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets,
                                                            Consumer<TransferSummary> progress) {
        return CompletableFuture.supplyAsync(() -> {
            List<UserDevice> safeTargets = targets == null || targets.isEmpty() ? demo.loadRecentDevices().join() : targets;
            TransferSummary summary = tx.run(files, safeTargets, settings.load(), progress);
            recent.remember(safeTargets);
            return summary;
        }, transferQueue);
    }

    // 设置接收前确认回调
    @Override
    public void setRxAsk(RxAsk ask) {
        rx.setAsk(ask);
    }

    // 设置接收进度回调
    @Override
    public void setRxProgress(RxProgress progress) {
        rx.setProgress(progress);
    }

    // 更新用户资料信息
    @Override
    public void updateProfile(Profile profile) {
        auth.updateProfile(profile);
        lan.updateSelf(profile);
        if (profile != null) {
            rx.updateStatus(profile.status());
        }
    }

    // 更新用户在线状态
    @Override
    public void updateStatus(UserStatus status, String customText) {
        auth.updateStatus(status, customText);
        lan.updateStatus(status);
        rx.updateStatus(status);
    }

    // 更新系统设置参数
    @Override
    public void updateSettings(SystemSettings settings) {
        this.settings.save(settings);
        lan.updateGroup(settings == null ? "" : settings.groupCode());
    }
}
