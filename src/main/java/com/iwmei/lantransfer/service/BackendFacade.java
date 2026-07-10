package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

// 前后端交互接口，定义界面需要的业务能力
public interface BackendFacade {
    // 登录功能的后端调用入口
    CompletableFuture<AuthResult> login(LoginRequest request);

    // 注册功能的后端调用入口
    CompletableFuture<AuthResult> register(RegisterRequest request);

    // 加载本地记住的最近登录账号
    CompletableFuture<String> loadRememberedAccount();

    // 加载近期传输对象列表
    CompletableFuture<List<UserDevice>> loadRecentDevices();

    // 加载全部可传输用户设备
    CompletableFuture<List<UserDevice>> loadAllDevices();

    // 保存本地传输分组并返回组目标
    CompletableFuture<UserDevice> saveGroup(String name, List<UserDevice> members);

    // 扫描局域网用户设备
    CompletableFuture<List<UserDevice>> scanLanDevices();

    // 加载系统设置参数
    CompletableFuture<SystemSettings> loadSettings();

    // 启动文件传输任务
    CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets);

    // 启动文件传输任务并推送传输中进度快照
    default CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets,
                                                            Consumer<TransferSummary> progress) {
        return startTransfer(files, targets);
    }

    // 暂停或继续当前发送任务
    default void pauseTransfer(boolean paused) {
    }

    // 设置接收前确认回调
    default void setRxAsk(RxAsk ask) {
    }

    // 设置接收进度回调
    default void setRxProgress(RxProgress progress) {
    }

    // 更新用户资料信息
    void updateProfile(Profile profile);

    // 更新用户在线状态
    void updateStatus(UserStatus status, String customText);

    // 更新系统设置参数
    void updateSettings(SystemSettings settings);
}
