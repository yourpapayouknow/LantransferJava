package com.iwmei.lantransfer.controller;

import com.iwmei.lantransfer.model.*;
import com.iwmei.lantransfer.service.BackendFacade;
import com.iwmei.lantransfer.service.MockBackendFacade;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class AppController {
    private final BackendFacade backend = new MockBackendFacade();

    public CompletableFuture<AuthResult> login(LoginRequest request) {
        return backend.login(request);
    }

    public CompletableFuture<AuthResult> register(RegisterRequest request) {
        return backend.register(request);
    }

    public CompletableFuture<List<UserDevice>> loadRecentDevices() {
        return backend.loadRecentDevices();
    }

    public CompletableFuture<List<UserDevice>> loadAllDevices() {
        return backend.loadAllDevices();
    }

    public CompletableFuture<List<UserDevice>> scanLanDevices() {
        return backend.scanLanDevices();
    }

    public CompletableFuture<TransferSummary> startTransfer(List<TransferFile> files, List<UserDevice> targets) {
        return backend.startTransfer(files, targets);
    }
}
