package com.iwmei.lantransfer.service;
import com.iwmei.lantransfer.model.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
public interface BackendFacade {
    CompletableFuture<AuthResult> login(LoginRequest request);
    CompletableFuture<AuthResult> register(RegisterRequest request);
    CompletableFuture<String> loadRememberedAccount();
    CompletableFuture<List<UserDevice>> loadRecentDevices();
    CompletableFuture<List<UserDevice>> loadAllDevices();
    CompletableFuture<List<Group>> loadGroups();
    CompletableFuture<UserDevice> saveGroup(String name, String code, List<UserDevice> members);
    CompletableFuture<UserDevice> updateGroup(String oldName, String name, String code, List<UserDevice> members);
    CompletableFuture<List<UserDevice>> scanLanDevices();
    CompletableFuture<SystemSettings> loadSettings();
    CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets);
    default CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets,
                                                            Consumer<TransferSummary> progress) {
        return startTransfer(files, targets);
    }
    CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets,
                                                     String code, Consumer<TransferSummary> progress);
    default void pauseTransfer(boolean paused) {
    }
    default void setRxAsk(RxAsk ask) {
    }
    default void setRxProgress(RxProgress progress) {
    }
    void updateProfile(Profile profile);
    void updateStatus(UserStatus status, String customText);
    void updateSettings(SystemSettings settings);
}
