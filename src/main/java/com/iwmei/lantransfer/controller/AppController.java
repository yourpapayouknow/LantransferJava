package com.iwmei.lantransfer.controller;

import com.iwmei.lantransfer.model.*;
import com.iwmei.lantransfer.service.BackendFacade;
import com.iwmei.lantransfer.service.LocalBackend;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

// 应用控制器，承接界面事件并转发给业务服务
public final class AppController {
    private final BackendFacade backend = new LocalBackend();

    // 登录功能的后端调用入口
    public CompletableFuture<AuthResult> login(LoginRequest request) {
        return backend.login(request);
    }

    // 注册功能的后端调用入口
    public CompletableFuture<AuthResult> register(RegisterRequest request) {
        return backend.register(request);
    }

    // 加载本地记住的最近登录账号
    public CompletableFuture<String> loadRememberedAccount() {
        return backend.loadRememberedAccount();
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

    // 加载系统设置参数
    public CompletableFuture<SystemSettings> loadSettings() {
        return backend.loadSettings();
    }

    // 启动文件传输任务
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets) {
        return backend.startTransfer(files, targets);
    }

    // 启动文件传输任务并接收传输中进度快照
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets,
                                                            Consumer<TransferSummary> progress) {
        return backend.startTransfer(files, targets, progress);
    }

    // 更新用户资料信息
    public void updateProfile(Profile profile) {
        backend.updateProfile(profile);
    }

    // 更新用户在线状态
    public void updateStatus(UserStatus status, String customText) {
        backend.updateStatus(status, customText);
    }

    // 更新系统设置参数
    public void updateSettings(SystemSettings settings) {
        backend.updateSettings(settings);
    }
}
