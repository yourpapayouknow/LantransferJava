package com.iwmei.lantransfer.service;
import com.iwmei.lantransfer.model.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// 本地业务后端，负责组合账号、扫描、分组、接收和发送等真实本地功能
public final class LocalBackend implements BackendFacade {
    private final AuthStore auth = new AuthStore();
    private final SettingsStore settings = new SettingsStore();
    private final RecentStore recent = new RecentStore();
    private final GroupStore groups = new GroupStore();
    private final UdpTx tx = new UdpTx();
    private final UdpRx rx = new UdpRx(settings);
    private final LanPeer lan = new LanPeer();
    private final ExecutorService transferQueue = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "lantransfer-tx-queue");
        thread.setDaemon(true);
        return thread;
    });
    public LocalBackend() {
        lan.updateGroup(settings.load().groupCode());
        rx.start();
    }
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
    @Override
    public CompletableFuture<AuthResult> register(RegisterRequest request) {
        return CompletableFuture.supplyAsync(() -> auth.register(request));
    }
    @Override
    public CompletableFuture<String> loadRememberedAccount() {
        return CompletableFuture.supplyAsync(auth::rememberedAccount);
    }
    @Override
    public CompletableFuture<List<UserDevice>> loadRecentDevices() {
        return CompletableFuture.supplyAsync(() -> {
            List<UserDevice> devices = new java.util.ArrayList<>(groups.targets());
            for (UserDevice device : recent.load()) {
                if (devices.stream().noneMatch(old -> old.id().equals(device.id()))) {
                    devices.add(device);
                }
            }
            return devices;
        });
    }
    @Override
    public CompletableFuture<List<UserDevice>> loadAllDevices() {
        return CompletableFuture.supplyAsync(lan::knownDevices);
    }
    @Override
    public CompletableFuture<List<Group>> loadGroups() {
        return CompletableFuture.supplyAsync(groups::all);
    }
    @Override
    public CompletableFuture<UserDevice> saveGroup(String name, String code, List<UserDevice> members) {
        return CompletableFuture.supplyAsync(() -> groups.save(name, code, members));
    }
    @Override
    public CompletableFuture<UserDevice> updateGroup(String oldName, String name, String code, List<UserDevice> members) {
        return CompletableFuture.supplyAsync(() -> groups.update(oldName, name, code, members));
    }
    @Override
    public CompletableFuture<List<UserDevice>> scanLanDevices() {
        return CompletableFuture.supplyAsync(lan::scan);
    }
    @Override
    public CompletableFuture<SystemSettings> loadSettings() {
        return CompletableFuture.supplyAsync(settings::load);
    }
    @Override
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets) {
        return startTransfer(files, targets, summary -> {
        });
    }
    @Override
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets,
                                                            Consumer<TransferSummary> progress) {
        return startTransfer(files, targets, "", progress);
    }
    @Override
    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets,
                                                            String code, Consumer<TransferSummary> progress) {
        return CompletableFuture.supplyAsync(() -> {
            List<UserDevice> requested = targets == null ? List.of() : targets;
            List<UserDevice> safeTargets = groups.expand(requested);
            TransferSummary summary = tx.run(files, safeTargets, settings.load(), code, progress);
            recent.remember(requested);
            return summary;
        }, transferQueue);
    }
    @Override
    public void pauseTransfer(boolean paused) {
        tx.setPaused(paused);
    }
    @Override
    public void setRxAsk(RxAsk ask) {
        rx.setAsk(ask);
    }
    @Override
    public void setRxProgress(RxProgress progress) {
        rx.setProgress(progress);
    }
    @Override
    public void updateProfile(Profile profile) {
        auth.updateProfile(profile);
        lan.updateSelf(profile);
        if (profile != null) {
            rx.updateStatus(profile.status());
        }
    }
    @Override
    public void updateStatus(UserStatus status, String customText) {
        auth.updateStatus(status, customText);
        lan.updateStatus(status, customText);
        rx.updateStatus(status);
    }
    @Override
    public void updateSettings(SystemSettings settings) {
        this.settings.save(settings);
        lan.updateGroup(settings == null ? "" : settings.groupCode());
    }
}
