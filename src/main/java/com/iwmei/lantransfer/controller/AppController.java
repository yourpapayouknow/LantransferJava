package com.iwmei.lantransfer.controller;
import com.iwmei.lantransfer.model.*;
import com.iwmei.lantransfer.service.BackendFacade;
import com.iwmei.lantransfer.service.LocalBackend;
import com.iwmei.lantransfer.service.RxAsk;
import com.iwmei.lantransfer.service.RxProgress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
public final class AppController {
    private final BackendFacade backend = new LocalBackend();
    public CompletableFuture<AuthResult> login(LoginRequest request) {
        return backend.login(request);
    }
    public CompletableFuture<AuthResult> register(RegisterRequest request) {
        return backend.register(request);
    }
    public CompletableFuture<String> loadRememberedAccount() {
        return backend.loadRememberedAccount();
    }
    public CompletableFuture<List<UserDevice>> loadRecentDevices() {
        return backend.loadRecentDevices();
    }
    public CompletableFuture<List<UserDevice>> loadAllDevices() {
        return backend.loadAllDevices();
    }
    public CompletableFuture<List<Group>> loadGroups() {
        return backend.loadGroups();
    }
    public CompletableFuture<UserDevice> saveGroup(String name, String code, List<UserDevice> members) {
        return backend.saveGroup(name, code, members);
    }
    public CompletableFuture<UserDevice> updateGroup(String oldName, String name, String code, List<UserDevice> members) {
        return backend.updateGroup(oldName, name, code, members);
    }
    public CompletableFuture<List<UserDevice>> scanLanDevices() {
        return backend.scanLanDevices();
    }
    public CompletableFuture<SystemSettings> loadSettings() {
        return backend.loadSettings();
    }
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets) {
        return backend.startTransfer(files, targets);
    }
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets,
                                                            Consumer<TransferSummary> progress) {
        return backend.startTransfer(files, targets, progress);
    }
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets,
                                                            String code, Consumer<TransferSummary> progress) {
        return backend.startTransfer(files, targets, code, progress);
    }
    public void pauseTransfer(boolean paused) {
        backend.pauseTransfer(paused);
    }
    public void setRxAsk(RxAsk ask) {
        backend.setRxAsk(ask);
    }
    public void setRxProgress(RxProgress progress) {
        backend.setRxProgress(progress);
    }
    public void updateProfile(Profile profile) {
        backend.updateProfile(profile);
    }
    public CompletableFuture<Void> updateStatus(UserStatus status, String customText) {
        return CompletableFuture.runAsync(() -> backend.updateStatus(status, customText));
    }
    public void updateSettings(SystemSettings settings) {
        backend.updateSettings(settings);
    }
}
