package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface BackendFacade {
    CompletableFuture<AuthResult> login(LoginRequest request);

    CompletableFuture<AuthResult> register(RegisterRequest request);

    CompletableFuture<List<UserDevice>> loadRecentDevices();

    CompletableFuture<List<UserDevice>> loadAllDevices();

    CompletableFuture<List<UserDevice>> scanLanDevices();

    CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets);

    void updateProfile(Profile profile);

    void updateStatus(UserStatus status, String customText);

    void updateSettings(SystemSettings settings);
}
