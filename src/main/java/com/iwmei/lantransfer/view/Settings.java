package com.iwmei.lantransfer.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

final class Settings {
    private final MainWindow app;

    Settings(MainWindow app) {
        this.app = app;
    }

    void showSettingsPage() {
        VBox page = new VBox(14);
        page.getStyleClass().add("page-content");
        VBox section = app.glassSection("系统设置");
        section.getChildren().addAll(
                settingsRow("本机局域网 IP", "在局域网内，其他设备可通过以下地址发现并连接到本机。", ipInfo()),
                settingsRow("传输速度限制", "设置全局的上传和下载速度限制（0 表示不限制）。", speedLimitControls()),
                settingsRow("失败重试次数", "文件传输失败时的自动重试次数设置。", retryControls()),
                settingsRow("界面颜色自定义", "自定义应用的主题色（重启后生效）。", colorControls()),
                settingsRow("字体设置", "自定义界面字体及大小（重启后生效）。", fontControls()),
                settingsRow("缩放比例", "调整界面整体显示缩放。", zoomControls()),
                settingsRow("语言设置", "设置界面显示语言。", languageControls()),
                settingsRow("启动设置", "控制软件启动后的默认行为。", startupControls())
        );
        page.getChildren().add(section);
        app.setMainPage("系统设置", page, true, true);
    }

    private HBox ipInfo() {
        return new HBox(12, app.ipColumn("IPv4 地址", "192.168.1.100"), app.ipColumn("IPv6 地址", "fe80::1a2b:3c4d"));
    }

    private VBox speedLimitControls() {
        VBox root = new VBox(10, app.limitRow("上传速度限制", 10), app.limitRow("下载速度限制", 20));
        root.setMaxWidth(360);
        return root;
    }

    private HBox retryControls() {
        Spinner<Integer> retries = new Spinner<>(0, 10, 3);
        retries.getStyleClass().add("dark-spinner");
        app.fixedWidth(retries, 72);
        return new HBox(12, retries, app.mutedLabel("次", 15));
    }

    private VBox colorControls() {
        VBox root = new VBox(10);
        HBox swatches = new HBox(8);
        for (String color : List.of("#ff8500", "#2f80ed", "#2ecc40", "#ff5353", "#8a52d8")) {
            StackPane swatch = app.colorSwatch(color, color.equalsIgnoreCase(app.accentColor));
            swatch.setOnMouseClicked(event -> {
                app.accentColor = color;
                showSettingsPage();
            });
            swatches.getChildren().add(swatch);
        }
        TextField current = app.textField(app.accentColor);
        app.fixedWidth(current, 112);
        root.getChildren().addAll(swatches, new HBox(10, app.mutedLabel("自定义颜色", 15), current));
        return root;
    }

    private VBox fontControls() {
        VBox root = new VBox(10);
        HBox displayRow = new HBox(8);
        displayRow.setAlignment(Pos.CENTER_LEFT);
        displayRow.getChildren().addAll(app.mutedLabel("字体展示", 15), app.tabPill("按钮", null, true),
                app.tabPill("标签", null, false), app.fixedWidth(app.textField("输入框"), 90), app.checkBox("选项", true));
        ComboBox<String> font = app.comboBox("Microsoft YaHei");
        app.fixedWidth(font, 156);
        TextField size = app.textField("14");
        app.fixedWidth(size, 76);
        root.getChildren().addAll(displayRow, new HBox(10, app.mutedLabel("字体设置", 15), font, size));
        return root;
    }

    private VBox zoomControls() {
        TextField zoom = app.textField("100%");
        app.fixedWidth(zoom, 132);
        return new VBox(8, zoom);
    }

    private HBox languageControls() {
        ComboBox<String> language = app.comboBox(app.profile == null ? "简体中文" : app.profile.language());
        app.fixedWidth(language, 132);
        return new HBox(language);
    }

    private HBox startupControls() {
        return new HBox(16, app.checkBox("开机自启动", false), app.checkBox("启动后最小化到系统托盘", true), app.checkBox("传输完成后播放提示音", true));
    }

    private HBox settingsRow(String title, String description, Node controls) {
        VBox text = new VBox(4, app.titleLabel(title, 20), app.mutedLabel(description, 14));
        HBox row = new HBox(18);
        row.getStyleClass().add("settings-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(text, app.separatorVertical(), controls);
        HBox.setHgrow(text, Priority.ALWAYS);
        return row;
    }
}
