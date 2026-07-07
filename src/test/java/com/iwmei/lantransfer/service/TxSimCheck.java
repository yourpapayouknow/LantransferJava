package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.TransferFile;
import com.iwmei.lantransfer.model.TransferSummary;
import com.iwmei.lantransfer.model.UserDevice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// TxSim 的无框架自检入口
public final class TxSimCheck {
    // 阻止自检类被实例化
    private TxSimCheck() {
    }

    // 运行在线成功、离线失败和三次重试统计检查
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("lantransfer-tx-check", ".txt");
        try {
            Files.writeString(file, "hello");
            UserDevice online = new UserDevice("u1", "李四", "PC-1", DeviceStatus.ONLINE, "刚刚", "李", "#4f7bd8", false);
            UserDevice offline = new UserDevice("u2", "王五", "PC-2", DeviceStatus.OFFLINE, "1 小时前", "王", "#35c6ca", false);
            TransferSummary summary = new TxSim().run(List.of(new TransferFile("a.txt", "5 B", file)), List.of(online, offline));
            require(summary.targetCount() == 2, "target count should match");
            require(summary.successCount() == 1, "online target should succeed");
            require(summary.failedCount() == 1, "offline target should fail");
            require(summary.retryCount() == 3, "offline target should retry three times");
            require(summary.tasks().size() == 2, "one file to two targets should create two rows");
            require(summary.logs().size() == 4, "start, two targets, end logs should exist");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    // 断言条件为真
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
