package com.iwmei.lantransfer.view;

import com.iwmei.lantransfer.model.SystemSettings;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.List;

// 系统设置页面逻辑
final class Settings {
    private final MainWindow app;
    private TextField uploadLimit;
    private TextField downloadLimit;
    private Spinner<Integer> retryCount;
    private TextField accentInput;
    private ComboBox<String> fontFamily;
    private TextField fontSize;
    private TextField zoomPercent;
    private TextField receiveDir;
    private ComboBox<String> language;
    private CheckBox autoStart;
    private CheckBox startMinimized;
    private CheckBox soundOnComplete;

    // 初始化系统设置页面对象
    Settings(MainWindow app) {
        this.app = app;
    }

    // 显示系统设置页面
    void showSettingsPage() {
        app.controller.loadSettings().thenAccept(settings -> Platform.runLater(() -> render(settings)));
    }

    // 渲染系统设置页面
    private void render(SystemSettings settings) {
        app.currentSettings = settings;
        app.accentColor = settings.accentColor();
        VBox page = new VBox(14);
        page.getStyleClass().add("page-content");
        VBox section = app.glassSection("系统设置");
        section.getChildren().addAll(
                settingsRow("本机局域网 IP", "在局域网内，其他设备可通过以下地址发现并连接到本机。", ipInfo(settings)),
                settingsRow("传输速度限制", "设置全局的上传和下载速度限制（0 表示不限制）。", speedLimitControls(settings)),
                settingsRow("失败重试次数", "文件传输失败时的自动重试次数设置。", retryControls(settings)),
                settingsRow("界面颜色自定义", "自定义应用的主题色（保存后生效）。", colorControls(settings)),
                settingsRow("字体设置", "自定义界面字体及大小（保存后生效）。", fontControls(settings)),
                settingsRow("缩放比例", "调整界面整体显示缩放。", zoomControls(settings)),
                settingsRow("接收目录", "设置接收文件保存位置，真实 UDP 接收服务会写入这里。", receiveDirControls(settings)),
                settingsRow("语言设置", "设置界面显示语言。", languageControls(settings)),
                settingsRow("启动设置", "控制软件启动后的默认行为。", startupControls(settings)),
                saveControls(settings)
        );
        page.getChildren().add(section);
        app.setMainPage("系统设置", page, true, true);
    }

    // 构建本机局域网地址信息
    private HBox ipInfo(SystemSettings settings) {
        return new HBox(12, app.ipColumn("IPv4 地址", settings.ipv4()), app.ipColumn("IPv6 地址", settings.ipv6()));
    }

    // 构建传输速度限制输入区域
    private VBox speedLimitControls(SystemSettings settings) {
        uploadLimit = app.textField("上传速度限制");
        uploadLimit.setText(String.valueOf(settings.uploadLimit()));
        downloadLimit = app.textField("下载速度限制");
        downloadLimit.setText(String.valueOf(settings.downloadLimit()));
        app.fixedWidth(uploadLimit, 90);
        app.fixedWidth(downloadLimit, 90);
        VBox root = new VBox(10,
                new HBox(10, app.mutedLabel("上传速度限制", 15), uploadLimit, app.mutedLabel("MB/s", 14)),
                new HBox(10, app.mutedLabel("下载速度限制", 15), downloadLimit, app.mutedLabel("MB/s", 14)));
        root.setMaxWidth(360);
        return root;
    }

    // 构建失败重试次数输入区域
    private HBox retryControls(SystemSettings settings) {
        retryCount = new Spinner<>(0, 10, settings.maxRetries());
        retryCount.getStyleClass().add("dark-spinner");
        app.fixedWidth(retryCount, 72);
        return new HBox(12, retryCount, app.mutedLabel("次", 15));
    }

    // 构建主题颜色设置区域
    private VBox colorControls(SystemSettings settings) {
        VBox root = new VBox(10);
        HBox swatches = new HBox(8);
        for (String color : List.of("#ff8500", "#2f80ed", "#2ecc40", "#ff5353", "#8a52d8")) {
            StackPane swatch = app.colorSwatch(color, color.equalsIgnoreCase(settings.accentColor()));
            swatch.setOnMouseClicked(event -> render(withAccent(settings, color)));
            swatches.getChildren().add(swatch);
        }
        accentInput = app.textField("自定义颜色");
        accentInput.setText(settings.accentColor());
        app.fixedWidth(accentInput, 112);
        root.getChildren().addAll(swatches, new HBox(10, app.mutedLabel("自定义颜色", 15), accentInput));
        return root;
    }

    // 构建字体设置区域
    private VBox fontControls(SystemSettings settings) {
        VBox root = new VBox(10);
        HBox displayRow = new HBox(8);
        displayRow.setAlignment(Pos.CENTER_LEFT);
        displayRow.getChildren().addAll(app.mutedLabel("字体展示", 15), app.tabPill("按钮", null, true),
                app.tabPill("标签", null, false), app.fixedWidth(app.textField("输入框"), 90), app.checkBox("选项", true));
        fontFamily = app.comboBox(settings.fontFamily());
        app.fixedWidth(fontFamily, 156);
        fontSize = app.textField("字体大小");
        fontSize.setText(String.valueOf(settings.fontSize()));
        app.fixedWidth(fontSize, 76);
        root.getChildren().addAll(displayRow, new HBox(10, app.mutedLabel("字体设置", 15), fontFamily, fontSize));
        return root;
    }

    // 构建界面缩放设置区域
    private VBox zoomControls(SystemSettings settings) {
        zoomPercent = app.textField("缩放比例");
        zoomPercent.setText(settings.zoomPercent() + "%");
        app.fixedWidth(zoomPercent, 132);
        return new VBox(8, zoomPercent);
    }

    // 构建接收目录设置区域
    private HBox receiveDirControls(SystemSettings settings) {
        receiveDir = app.textField("接收目录");
        receiveDir.setText(settings.receiveDir());
        Button choose = app.outlineButton("选择");
        choose.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择接收目录");
            File current = new File(receiveDir.getText());
            if (current.isDirectory()) {
                chooser.setInitialDirectory(current);
            }
            File selected = chooser.showDialog(app.stage);
            if (selected != null) {
                receiveDir.setText(selected.getAbsolutePath());
            }
        });
        HBox row = new HBox(10, receiveDir, choose);
        HBox.setHgrow(receiveDir, Priority.ALWAYS);
        return row;
    }

    // 构建语言设置区域
    private HBox languageControls(SystemSettings settings) {
        language = app.comboBox(settings.language());
        app.fixedWidth(language, 132);
        return new HBox(language);
    }

    // 构建启动行为设置区域
    private HBox startupControls(SystemSettings settings) {
        autoStart = app.checkBox("开机自启动", settings.autoStart());
        startMinimized = app.checkBox("启动后最小化到系统托盘", settings.startMinimized());
        soundOnComplete = app.checkBox("传输完成后播放提示音", settings.soundOnComplete());
        return new HBox(16, autoStart, startMinimized, soundOnComplete);
    }

    // 构建保存设置按钮区域
    private HBox saveControls(SystemSettings base) {
        Button save = app.primaryButton("保存设置");
        save.setOnAction(event -> {
            SystemSettings settings = readSettings(base);
            app.controller.updateSettings(settings);
            app.currentSettings = settings;
            app.toast("设置已保存");
            render(settings);
        });
        HBox row = new HBox(app.fixedWidth(save, 160));
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    // 从控件读取系统设置
    private SystemSettings readSettings(SystemSettings base) {
        return new SystemSettings(base.ipv4(), base.ipv6(),
                intValue(uploadLimit, base.uploadLimit()),
                intValue(downloadLimit, base.downloadLimit()),
                retryCount.getValue(),
                colorValue(base),
                fontFamily.getValue(),
                intValue(fontSize, base.fontSize()),
                intValue(zoomPercent, base.zoomPercent()),
                textValue(receiveDir, base.receiveDir()),
                language.getValue(),
                autoStart.isSelected(),
                startMinimized.isSelected(),
                soundOnComplete.isSelected());
    }

    // 替换主题色并保留其它设置
    private SystemSettings withAccent(SystemSettings settings, String color) {
        return new SystemSettings(settings.ipv4(), settings.ipv6(), settings.uploadLimit(), settings.downloadLimit(),
                settings.maxRetries(), color, settings.fontFamily(), settings.fontSize(), settings.zoomPercent(),
                settings.receiveDir(), settings.language(), settings.autoStart(), settings.startMinimized(), settings.soundOnComplete());
    }

    // 读取整数输入框
    private int intValue(TextField field, int fallback) {
        try {
            return Integer.parseInt(field.getText().replace("%", "").trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    // 读取主题色输入框
    private String colorValue(SystemSettings base) {
        String color = accentInput.getText().trim();
        return color.matches("#[0-9a-fA-F]{6}") ? color : base.accentColor();
    }

    // 读取文本输入框
    private String textValue(TextField field, String fallback) {
        String text = field.getText().trim();
        return text.isBlank() ? fallback : text;
    }

    // 构建系统设置单行配置项
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
