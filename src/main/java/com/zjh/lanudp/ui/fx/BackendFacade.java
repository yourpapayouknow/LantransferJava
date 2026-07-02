package com.zjh.lanudp.ui.fx;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

interface BackendFacade {
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

enum UserStatus {
    DEFAULT,
    ONLINE,
    BUSY,
    INVISIBLE,
    OFFLINE
}

enum DeviceStatus {
    ONLINE,
    OFFLINE
}

record LoginRequest(String account, String password, boolean rememberMe) {
}

record RegisterRequest(String account, String password, String deviceName) {
}

record AuthResult(boolean success, boolean pendingReview, String message, Profile profile) {
}

record Profile(String nickname, String userId, String deviceName, String signature, LocalDateTime registeredAt,
               LocalDateTime lastLoginAt, String version, String language) {
}

record UserDevice(String id, String nickname, String deviceName, DeviceStatus status, String lastSeen,
                  String avatarText, String color, boolean imageAvatar) {
}

record TransferFile(String fileName, String size, Path path) {
}

record TransferTask(String fileName, UserDevice target, int progressPercent, String size, String speed,
                    String elapsed, String status, int retries) {
}

record TransferSummary(int targetCount, int successCount, int failedCount, int retryCount, String elapsed,
                       List<String> logs, List<TransferTask> tasks) {
}

record SystemSettings(String ipv4, String ipv6, int uploadLimit, int downloadLimit, int maxRetries,
                      String accentColor, String fontFamily, int fontSize, int zoomPercent) {
}
