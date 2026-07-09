package com.iwmei.lantransfer.view;

import com.iwmei.lantransfer.model.UserDevice;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

// 局域网扫描页面逻辑
final class Scan {
    private final MainWindow app;
    private int scanRunId;

    // 初始化局域网扫描页面对象
    Scan(MainWindow app) {
        this.app = app;
    }

    // 显示局域网扫描页面
    void showScanPage() {
        int runId = ++scanRunId;
        renderScanningPage(runId);
        app.controller.scanLanDevices()
                .exceptionally(error -> List.of())
                .thenAccept(devices -> Platform.runLater(() -> {
                    if (runId != scanRunId) {
                        return;
                    }
                    renderCompletedPage(devices);
                }));
    }

    // 渲染扫描进行中状态
    private void renderScanningPage(int runId) {
        VBox page = scanPage();
        Button cancel = app.outlineButton("取消扫描");
        cancel.setOnAction(event -> {
            if (runId == scanRunId) {
                scanRunId++;
            }
            app.showUserListPage();
        });
        HBox scanning = new HBox(12, app.smallSpinner(), app.mutedLabel("正在扫描中，请稍候...", 18));
        scanning.setAlignment(Pos.CENTER);
        page.getChildren().addAll(app.titleLabel("正在扫描局域网用户...", 34), app.mutedLabel("发现附近设备，建立连接通道", 18),
                app.radar(List.of()), scanning, app.noticePill("◌", "提示：隐身用户无法被扫描发现"), app.fixedWidth(cancel, 160));
        app.setMainPage("扫描局域网", page, false, true);
    }

    // 渲染扫描完成结果
    private void renderCompletedPage(List<UserDevice> devices) {
        VBox page = scanPage();
        Button viewUsers = app.primaryButton("查看用户列表");
        viewUsers.setOnAction(event -> app.showUserListPage());
        Button again = app.outlineButton("重新扫描");
        again.setOnAction(event -> showScanPage());
        HBox actions = new HBox(12, app.fixedWidth(viewUsers, 160), app.fixedWidth(again, 160));
        actions.setAlignment(Pos.CENTER);
        page.getChildren().addAll(app.titleLabel("扫描完成", 34), app.mutedLabel("发现 " + devices.size() + " 个可传输用户", 18),
                app.radar(devices), app.noticePill("✓", "用户列表已更新，隐身用户无法被扫描发现"), actions);
        app.setMainPage("扫描局域网", page, false, true);
    }

    // 创建扫描页基础布局
    private VBox scanPage() {
        VBox page = new VBox(22);
        page.getStyleClass().add("scan-page");
        page.setAlignment(Pos.CENTER);
        return page;
    }
}
