package com.iwmei.lantransfer.controller;

import com.iwmei.lantransfer.model.*;
import com.iwmei.lantransfer.service.BackendFacade;
import com.iwmei.lantransfer.service.MockBackendFacade;

import java.util.List;
import java.util.concurrent.CompletableFuture;

// 应用控制器，承接界面事件并转发给业务服务
public final class AppController {
    private final BackendFacade backend = new MockBackendFacade();

    // 登录功能的后端调用入口
    public CompletableFuture<AuthResult> login(LoginRequest request) {
        return backend.login(request);
    }

    // 注册功能的后端调用入口
    public CompletableFuture<AuthResult> register(RegisterRequest request) {
        return backend.register(request);
    }

    // 加载近期传输对象列表
    public CompletableFuture<List<UserDevice>> loadRecentDevices() {
        return backend.loadRecentDevices();
    }

    // 加载全部可传输用户设备
    public CompletableFuture<List<UserDevice>> loadAllDevices() {
        return backend.loadAllDevices();
    }

    // 扫描局域网用户设备
    public CompletableFuture<List<UserDevice>> scanLanDevices() {
        return backend.scanLanDevices();
    }

    // 启动文件传输任务
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets) {
        return backend.startTransfer(files, targets);
    }
}
