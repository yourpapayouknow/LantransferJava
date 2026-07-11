package com.iwmei.lantransfer.view;

import com.iwmei.lantransfer.model.SystemSettings;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.List;
import java.util.Locale;

// 系统设置页面逻辑
final class Settings {
    private final MainWindow app;
    private TextField uploadLimit;
    private TextField downloadLimit;
    private Spinner<Integer> retryCount;
    private TextField accentInput;
    private ComboBox<String> fontFamily;
    private TextField fontSize;
    private Spinner<Integer> zoomPercent;
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
        page.setAlignment(Pos.TOP_CENTER);
        VBox section = new VBox(0);
        section.setMaxWidth(Double.MAX_VALUE);
        section.setFillWidth(true);
        section.getChildren().addAll(
                settingsRow("本机局域网IP", "", ipInfo(settings)),
                settingsRow("传输速度限制", "不限速请设定为0", speedLimitControls(settings)),
                settingsRow("失败重试次数", "", retryControls(settings)),
                settingsRow("界面颜色自定义", "", colorControls(settings)),
                settingsRow("字体设置", "保存后生效", fontControls(settings)),
                settingsRow("缩放比例", "", zoomControls(settings)),
                settingsRow("接收目录", "", receiveDirControls(settings)),
                settingsRow("语言设置", "", languageControls(settings)),
                settingsRow("启动设置", "", startupControls(settings)),
                saveControls(settings)
        );
        page.getChildren().add(section);
        app.setMainPage("系统设置", page, true, true);
    }

    // 构建本机局域网地址信息
    private HBox ipInfo(SystemSettings settings) {
        return uiRow(12, app.ipColumn("IPv4地址", settings.ipv4()), app.ipColumn("IPv6地址", settings.ipv6()));
    }

    // 构建传输速度限制输入区域
    private HBox speedLimitControls(SystemSettings settings) {
        uploadLimit = app.textField("上传限制");
        uploadLimit.setText(String.valueOf(settings.uploadLimit()));
        downloadLimit = app.textField("下载限制");
        downloadLimit.setText(String.valueOf(settings.downloadLimit()));
        app.fixedWidth(uploadLimit, 90);
        app.fixedWidth(downloadLimit, 90);
        return uiRow(16,
                uiRow(8, app.mutedLabel("上传限制", 15), uploadLimit, app.mutedLabel("MB/s", 14)),
                uiRow(8, app.mutedLabel("下载限制", 15), downloadLimit, app.mutedLabel("MB/s", 14)));
    }

    // 构建失败重试次数输入区域
    private HBox retryControls(SystemSettings settings) {
        retryCount = new Spinner<>(0, 10, settings.maxRetries());
        retryCount.getStyleClass().add("dark-spinner");
        app.fixedWidth(retryCount, 72);
        return uiRow(12, retryCount, app.mutedLabel("次", 15));
    }

    // 构建主题颜色设置区域
    private HBox colorControls(SystemSettings settings) {
        HBox row = uiRow(8);
        List<String> colors = List.of("#ff8500", "#2f80ed", "#2ecc40", "#ff5353", "#8a52d8");
        for (String color : colors) {
            StackPane swatch = app.colorSwatch(color, color.equalsIgnoreCase(settings.accentColor()));
            swatch.setOnMouseClicked(event -> render(withAccent(settings, color)));
            row.getChildren().add(swatch);
        }
        StackPane custom = app.colorSwatch(settings.accentColor(), colors.stream().noneMatch(color -> color.equalsIgnoreCase(settings.accentColor())));
        custom.setOnMouseClicked(event -> render(withAccent(settings, colorValue(settings))));
        accentInput = app.textField("自定义");
        accentInput.setText(settings.accentColor());
        app.fixedWidth(accentInput, 112);
        row.getChildren().addAll(custom, app.mutedLabel("自定义", 15), accentInput);
        return row;
    }

    // 构建字体设置区域
    private HBox fontControls(SystemSettings settings) {
        fontFamily = fontBox(settings.fontFamily());
        app.fixedWidth(fontFamily, 156);
        fontSize = app.textField("字体大小");
        fontSize.setText(String.valueOf(settings.fontSize()));
        app.fixedWidth(fontSize, 76);
        return uiRow(10, fontFamily, fontSize);
    }

    // 构建系统中文字体下拉框
    private ComboBox<String> fontBox(String value) {
        ComboBox<String> combo = new ComboBox<>();
        for (String family : Font.getFamilies()) {
            if (chineseFont(family)) {
                combo.getItems().add(family);
            }
        }
        if (!combo.getItems().contains(value)) {
            combo.getItems().add(0, value);
        }
        combo.setValue(value);
        return combo;
    }

    // 判断字体名称是否常见中文字体
    private boolean chineseFont(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return name.matches(".*[宋黑楷仿隶圆等雅].*")
                || lower.contains("yahei")
                || lower.contains("jhenghei")
                || lower.contains("simsun")
                || lower.contains("simhei")
                || lower.contains("fangsong")
                || lower.contains("kaiti")
                || lower.contains("dengxian")
                || lower.contains("youyuan")
                || lower.contains("lisu")
                || lower.startsWith("st");
    }

    // 构建界面缩放设置区域
    private HBox zoomControls(SystemSettings settings) {
        zoomPercent = new Spinner<>(70, 200, zoomValue(settings), 10);
        zoomPercent.getStyleClass().add("dark-spinner");
        app.fixedWidth(zoomPercent, 96);
        return uiRow(8, zoomPercent, app.mutedLabel("%", 15));
    }

    // 把已有缩放值限制到设置页范围和步进
    private int zoomValue(SystemSettings settings) {
        int value = Math.max(70, Math.min(200, settings.zoomPercent()));
        return 70 + Math.round((value - 70) / 10.0f) * 10;
    }

    // 构建接收目录设置区域
    private HBox receiveDirControls(SystemSettings settings) {
        receiveDir = app.textField("接收目录");
        receiveDir.setText(settings.receiveDir());
        receiveDir.setMinWidth(360);
        receiveDir.setPrefWidth(500);
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
        HBox row = uiRow(10, receiveDir, choose);
        HBox.setHgrow(receiveDir, Priority.ALWAYS);
        return row;
    }

    // 构建语言设置区域
    private HBox languageControls(SystemSettings settings) {
        language = app.comboBox(settings.language());
        app.fixedWidth(language, 132);
        return uiRow(10, language);
    }

    // 构建启动行为设置区域
    private HBox startupControls(SystemSettings settings) {
        autoStart = app.checkBox("开机自启动", settings.autoStart());
        startMinimized = app.checkBox("启动后最小化到系统托盘", settings.startMinimized());
        soundOnComplete = app.checkBox("传输完成后播放提示音", settings.soundOnComplete());
        return uiRow(16, autoStart, startMinimized, soundOnComplete);
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
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(20, 0, 0, 0));
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
                zoomPercent.getValue(),
                textValue(receiveDir, base.receiveDir()),
                "",
                language.getValue(),
                autoStart.isSelected(),
                startMinimized.isSelected(),
                soundOnComplete.isSelected());
    }

    // 替换主题色并保留其它设置
    private SystemSettings withAccent(SystemSettings settings, String color) {
        return new SystemSettings(settings.ipv4(), settings.ipv6(), settings.uploadLimit(), settings.downloadLimit(),
                settings.maxRetries(), color, settings.fontFamily(), settings.fontSize(), settings.zoomPercent(),
                settings.receiveDir(), "", settings.language(), settings.autoStart(), settings.startMinimized(), settings.soundOnComplete());
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

    // 构建对齐的控件行
    private HBox uiRow(double gap, Node... nodes) {
        HBox row = new HBox(gap, nodes);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    // 构建系统设置单行配置项
    private GridPane settingsRow(String title, String description, HBox controls) {
        VBox text = new VBox(4, app.titleLabel(title, 20));
        text.setAlignment(Pos.CENTER_LEFT);
        if (!description.isBlank()) {
            text.getChildren().add(app.mutedLabel(description, 14));
        }
        GridPane row = new GridPane();
        row.getStyleClass().add("settings-row");
        row.setAlignment(Pos.CENTER);
        row.setHgap(18);
        row.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints titleColumn = new ColumnConstraints(210, 210, 210);
        ColumnConstraints dividerColumn = new ColumnConstraints(1, 1, 1);
        ColumnConstraints spacerColumn = new ColumnConstraints();
        spacerColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints controlsColumn = new ColumnConstraints(740, 740, 740);
        row.getColumnConstraints().addAll(titleColumn, dividerColumn, spacerColumn, controlsColumn);
        row.add(text, 0, 0);
        row.add(app.separatorVertical(), 1, 0);
        row.add(controls, 3, 0);
        GridPane.setHalignment(text, HPos.LEFT);
        GridPane.setValignment(text, VPos.CENTER);
        GridPane.setHalignment(controls, HPos.RIGHT);
        GridPane.setValignment(controls, VPos.CENTER);
        return row;
    }
}
