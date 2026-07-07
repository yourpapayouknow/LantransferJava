package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

// 本地业务后端，负责已落地功能并临时复用演示数据补齐未实现页面
public final class LocalBackend implements BackendFacade {
    private final AuthStore auth = new AuthStore();
    private final MockBackendFacade demo = new MockBackendFacade();
    private final TxSim tx = new TxSim();
    private final LanPeer lan = new LanPeer();

    // 登录功能的本地后端调用入口
    @Override
    public CompletableFuture<AuthResult> login(LoginRequest request) {
        return CompletableFuture.supplyAsync(() -> auth.login(request));
    }

    // 注册功能的本地后端调用入口
    @Override
    public CompletableFuture<AuthResult> register(RegisterRequest request) {
        return CompletableFuture.supplyAsync(() -> auth.register(request));
    }

    // 加载近期传输对象列表
    @Override
    public CompletableFuture<List<UserDevice>> loadRecentDevices() {
        return demo.loadRecentDevices();
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

    // 启动文件传输任务
    @Override
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets) {
        return CompletableFuture.supplyAsync(() -> tx.run(files, targets == null || targets.isEmpty() ? demo.loadRecentDevices().join() : targets));
    }

    // 更新用户资料信息
    @Override
    public void updateProfile(Profile profile) {
        demo.updateProfile(profile);
    }

    // 更新用户在线状态
    @Override
    public void updateStatus(UserStatus status, String customText) {
        demo.updateStatus(status, customText);
    }

    // 更新系统设置参数
    @Override
    public void updateSettings(SystemSettings settings) {
        demo.updateSettings(settings);
    }
}
