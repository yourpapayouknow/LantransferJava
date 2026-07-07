package com.iwmei.lantransfer.view;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

// 局域网扫描页面逻辑
final class Scan {
    private final MainWindow app;

    // 初始化局域网扫描页面对象
    Scan(MainWindow app) {
        this.app = app;
    }

    // 显示局域网扫描页面
    void showScanPage() {
        app.controller.scanLanDevices().thenAccept(devices -> Platform.runLater(() -> {
            VBox page = new VBox(22);
            page.getStyleClass().add("scan-page");
            page.setAlignment(Pos.CENTER);
            Button cancel = app.outlineButton("取消扫描");
            cancel.setOnAction(event -> app.showUserListPage());
            HBox scanning = new HBox(12, app.smallSpinner(), app.mutedLabel("正在扫描中，请稍候...", 18));
            scanning.setAlignment(Pos.CENTER);
            page.getChildren().addAll(app.titleLabel("正在扫描局域网用户...", 34), app.mutedLabel("发现附近设备，建立连接通道", 18),
                    app.radar(devices), scanning, app.noticePill("◌", "提示：隐身用户无法被扫描发现"), app.fixedWidth(cancel, 160));
            app.setMainPage("扫描局域网", page, false, true);
        }));
    }
}
