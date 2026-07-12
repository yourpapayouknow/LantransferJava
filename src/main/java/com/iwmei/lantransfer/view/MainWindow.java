package com.iwmei.lantransfer.view;

import com.iwmei.lantransfer.controller.AppController;
import com.iwmei.lantransfer.model.*;
import com.iwmei.lantransfer.util.FileIcons;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// 主窗口壳，负责窗口、导航和共享控件构建
public class MainWindow extends Application {
    static final String APP_TITLE = "极速互传 v1.0.0";
    static final String ACCENT_ORANGE = "#ff8500";
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static final double STARTUP_WIDTH = 860;
    static final double STARTUP_HEIGHT = 600;
    static final double AUTH_WIDTH = 780;
    static final double AUTH_HEIGHT = 700;
    static final double MAIN_WIDTH = 1240;
    static final double MAIN_HEIGHT = 820;
    static final double MAIN_MIN_WIDTH = 1040;
    static final double MAIN_MIN_HEIGHT = 640;

    final AppController controller = new AppController();
    final ObservableList<TransferFile> pendingFiles = FXCollections.observableArrayList();
    final List<UserDevice> recentTargets = new ArrayList<>();
    final List<UserDevice> selectedTargets = new ArrayList<>();

    Stage stage;
    Profile profile;
    TransferSummary currentSummary;
    SystemSettings currentSettings;
    String accentColor = ACCENT_ORANGE;
    boolean transferRunning;
    boolean transferPaused;
    boolean userListGridView;
    boolean recentTargetsLoaded;
    boolean startupTrayApplied;
    java.awt.TrayIcon trayIcon;
    int userListPage;
    double requestedWindowWidth = -1;
    double requestedWindowHeight = -1;
    double dragOffsetX;
    double dragOffsetY;

    final Auth auth = new Auth(this);
    final FileTransfer fileTransfer = new FileTransfer(this);
    final UserList userList = new UserList(this);
    final Mine mine = new Mine(this);
    final Settings settings = new Settings(this);


    // JavaFX 启动后初始化主窗口
    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle(APP_TITLE);
        controller.setRxAsk(this::confirmReceive);
        controller.setRxProgress(this::showRxProgress);
        controller.loadSettings().thenAccept(settings -> Platform.runLater(() -> {
            currentSettings = settings;
            accentColor = settings.accentColor();
            showAuth(false);
        })).exceptionally(error -> {
            Platform.runLater(() -> showAuth(false));
            return null;
        });
    }

    // 显示登录或注册入口页面
    void showAuth(boolean registerMode) {
        auth.show(registerMode);
    }

    // 显示注册审核等待页面
    void showReviewPending() {
        auth.showReviewPending();
    }

    // 显示文件传输主页面
    void showFileTransferPage() {
        fileTransfer.showFileTransferPage();
    }

    // 显示传输结果页面
    void showTransferResultPage() {
        fileTransfer.showTransferResultPage();
    }

    // 显示用户列表页面
    void showUserListPage() {
        userList.showUserListPage();
    }

    // 显示我的资料页面
    void showProfilePage() {
        mine.showProfilePage();
    }

    // 显示系统设置页面
    void showSettingsPage() {
        settings.showSettingsPage();
    }

    // 在忙碌状态下询问用户是否接收文件
    private boolean confirmReceive(String fileName, long bytes, String codeHash) {
        if (Platform.isFxApplicationThread()) {
            return showReceiveConfirm(fileName, bytes, codeHash);
        }
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        Platform.runLater(() -> answer.complete(showReceiveConfirm(fileName, bytes, codeHash)));
        try {
            return answer.get(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return false;
        }
    }

    // 显示接收确认对话框
    private boolean showReceiveConfirm(String fileName, long bytes, String codeHash) {
        if (codeHash != null && !codeHash.isBlank()) {
            return showCodeConfirm(fileName, bytes, codeHash);
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle("接收确认");
        alert.setHeaderText("收到文件：" + fileName);
        alert.setContentText("当前状态为忙碌，是否接收 " + bytes + " B？");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    // 显示传输口令校验对话框
    private boolean showCodeConfirm(String fileName, long bytes, String codeHash) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle("传输口令");
        alert.setHeaderText("收到文件：" + fileName);
        PasswordField code = passwordField("请输入传输口令");
        VBox content = new VBox(10, mutedLabel("大小：" + bytes + " B", 14), code);
        alert.getDialogPane().setContent(content);
        Button ok = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
        ok.setDisable(true);
        code.textProperty().addListener((unused, oldValue, newValue) -> ok.setDisable(!codeMatches(newValue, codeHash)));
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent() && codeMatches(code.getText(), codeHash);
    }

    // 展示接收端进度提示
    private void showRxProgress(String fileName, int percent) {
        if (percent >= 100) {
            playDoneSound();
        }
        if (Platform.isFxApplicationThread()) {
            toast("接收 " + fileName + " " + percent + "%");
            return;
        }
        Platform.runLater(() -> toast("接收 " + fileName + " " + percent + "%"));
    }

    // 把登录注册内容放入认证窗口壳
    void setAuthPage(Node body) {
        setWindow(windowShell(body), AUTH_WIDTH, AUTH_HEIGHT, AUTH_WIDTH, AUTH_HEIGHT);
    }

    // 把页面内容放入主窗口壳
    void setMainPage(String activeNav, Node page, boolean topActions, boolean footer) {
        setWindow(mainWindowShell(activeNav, page, topActions, footer), MAIN_WIDTH, MAIN_HEIGHT, MAIN_MIN_WIDTH, MAIN_MIN_HEIGHT);
    }


    // 创建等宽卡片网格布局
    GridPane cardGrid(int columns, double hgap, double vgap) {
        GridPane grid = new GridPane();
        grid.setMinWidth(0);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setHgap(hgap);
        grid.setVgap(vgap);
        for (int column = 0; column < columns; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / columns);
            constraints.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraints);
        }
        return grid;
    }

    // 按索引把卡片放入网格
    void addCard(GridPane grid, Node card, int index, int columns) {
        GridPane.setHgrow(card, Priority.ALWAYS);
        GridPane.setFillWidth(card, true);
        grid.add(card, index % columns, index / columns);
    }

    // 构建登录注册窗口外壳
    Parent windowShell(Node body) {
        VBox shell = new VBox();
        shell.getStyleClass().add("window-shell");
        shell.setMinSize(0, 0);
        shell.getChildren().addAll(titleBar(), body);
        VBox.setVgrow(body, Priority.ALWAYS);
        // 构建带主题色和圆角裁剪的根节点
        return appRoot(shell);
    }

    // 构建主界面窗口外壳
    Parent mainWindowShell(String activeNav, Node page, boolean topActions, boolean footer) {
        VBox shell = new VBox();
        shell.getStyleClass().add("window-shell");
        shell.setMinSize(0, 0);
        shell.getChildren().add(titleBar());

        HBox main = new HBox();
        main.getStyleClass().add("main-area");
        main.getChildren().add(sidebar(activeNav));

        VBox center = new VBox();
        center.getStyleClass().add("main-center");
        center.setMinSize(0, 0);
        if (topActions) {
            center.getChildren().add(mainTopbar());
        }
        ScrollPane scrollPane = new ScrollPane(page);
        scrollPane.getStyleClass().add("page-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setMinSize(0, 0);
        if (page instanceof Region region) {
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        center.getChildren().add(scrollPane);
        if (footer) {
            center.getChildren().add(statusFooter());
        }
        main.getChildren().add(center);
        HBox.setHgrow(center, Priority.ALWAYS);
        shell.getChildren().add(main);
        VBox.setVgrow(main, Priority.ALWAYS);
        // 构建带主题色和圆角裁剪的根节点
        return appRoot(shell);
    }

    // 构建带主题色和圆角裁剪的根节点
    Parent appRoot(Node shell) {
        StackPane root = new StackPane(shell);
        root.getStyleClass().add("app-root");
        root.setStyle(rootStyle(accentColor, currentSettings));
        if (shell instanceof Region region) {
            region.setMinSize(0, 0);
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            clipToRoundedWindow(region);
        }
        return root;
    }

    // 构建根节点主题、字体和缩放样式
    static String rootStyle(String accentColor, SystemSettings settings) {
        StringBuilder style = new StringBuilder("-color-accent: ").append(accentColor).append(";");
        if (settings != null) {
            int fontSize = Math.max(10, Math.min(32, settings.fontSize()));
            int zoom = Math.max(50, Math.min(200, settings.zoomPercent()));
            double scaled = fontSize * zoom / 100.0;
            style.append("-fx-font-family: \"").append(css(settings.fontFamily())).append("\";");
            style.append(String.format(Locale.ROOT, "-fx-font-size: %.2fpx;", scaled));
        }
        return style.toString();
    }

    // 转义 CSS 字符串
    private static String css(String value) {
        return (value == null || value.isBlank() ? "Microsoft YaHei" : value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // 把内容裁剪到圆角窗口范围内
    void clipToRoundedWindow(Region region) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        region.setClip(clip);
    }

    // 设置窗口场景尺寸和显示状态
    void setWindow(Parent root, double width, double height, double minWidth, double minHeight) {
        boolean firstShow = !stage.isShowing();
        boolean requestedSizeChanged = Math.abs(requestedWindowWidth - width) > 0.5
                || Math.abs(requestedWindowHeight - height) > 0.5;
        double sceneWidth = width;
        double sceneHeight = height;
        if (!firstShow && !requestedSizeChanged && stage.getScene() != null) {
            sceneWidth = Math.max(stage.getScene().getWidth(), minWidth);
            sceneHeight = Math.max(stage.getScene().getHeight(), minHeight);
        }
        Scene scene = new Scene(root, sceneWidth, sceneHeight);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(Objects.requireNonNull(MainWindow.class.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(minWidth);
        stage.setMinHeight(minHeight);
        if (firstShow || requestedSizeChanged) {
            stage.sizeToScene();
            stage.centerOnScreen();
        }
        requestedWindowWidth = width;
        requestedWindowHeight = height;
        if (firstShow) {
            stage.show();
        }
        applyStartupTray();
    }

    // 按启动设置隐藏到系统托盘
    private void applyStartupTray() {
        if (startupTrayApplied || profile == null || currentSettings == null) {
            return;
        }
        startupTrayApplied = true;
        if (currentSettings.autoStart() && currentSettings.startMinimized()) {
            hideToTray();
        }
    }

    // 构建自定义窗口标题栏
    HBox titleBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("title-bar");
        bar.setMinSize(0, 0);
        bar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(APP_TITLE);
        title.getStyleClass().add("window-title");
        Button minimize = windowButton("mdi2w-window-minimize", "最小化");
        minimize.setOnAction(event -> minimizeWindow());
        Button close = windowButton("mdi2w-window-close", "关闭");
        close.setOnAction(event -> stage.close());
        bar.getChildren().addAll(title, spacer(), minimize, close);
        enableDrag(bar);
        return bar;
    }

    // 执行窗口最小化或托盘隐藏
    private void minimizeWindow() {
        if (currentSettings != null && currentSettings.startMinimized()) {
            hideToTray();
        } else {
            stage.setIconified(true);
        }
    }

    // 隐藏窗口到系统托盘
    private void hideToTray() {
        if (ensureTray()) {
            stage.hide();
        } else {
            stage.setIconified(true);
        }
    }

    // 创建系统托盘图标
    private boolean ensureTray() {
        if (trayIcon != null) {
            return true;
        }
        if (!java.awt.SystemTray.isSupported()) {
            return false;
        }
        try {
            java.awt.PopupMenu menu = new java.awt.PopupMenu();
            java.awt.MenuItem show = new java.awt.MenuItem("显示");
            java.awt.MenuItem exit = new java.awt.MenuItem("退出");
            show.addActionListener(event -> restoreFromTray());
            exit.addActionListener(event -> exitFromTray());
            menu.add(show);
            menu.add(exit);
            trayIcon = new java.awt.TrayIcon(trayImage(), APP_TITLE, menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(event -> restoreFromTray());
            java.awt.SystemTray.getSystemTray().add(trayIcon);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    // 从系统托盘恢复窗口
    private void restoreFromTray() {
        Platform.runLater(() -> {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
        });
    }

    // 从系统托盘退出应用
    private void exitFromTray() {
        java.awt.SystemTray.getSystemTray().remove(trayIcon);
        Platform.exit();
    }

    // 生成系统托盘图标图片
    private java.awt.Image trayImage() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(awtAccent());
            graphics.fillOval(2, 2, 12, 12);
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillOval(6, 6, 4, 4);
            return image;
        } finally {
            graphics.dispose();
        }
    }

    // 转换主题色为 AWT 颜色
    private java.awt.Color awtAccent() {
        try {
            return java.awt.Color.decode(accentColor);
        } catch (Exception ignored) {
            return java.awt.Color.ORANGE;
        }
    }

    // 构建左侧导航栏
    VBox sidebar(String activeNav) {
        VBox sidebar = new VBox(20);
        sidebar.getStyleClass().add("sidebar");
        sidebar.getChildren().addAll(
                navItem("文件传输", activeNav, this::showFileTransferPage),
                navItem("用户列表", activeNav, this::showUserListPage),
                navItem("我的", activeNav, this::showProfilePage),
                navItem("系统设置", activeNav, this::showSettingsPage),
                spacer()
        );
        sidebar.getChildren().add(connectionInfo());
        return sidebar;
    }

    // 构建单个导航按钮
    Node navItem(String text, String activeNav, Runnable action) {
        HBox item = new HBox(new Label(text));
        item.getStyleClass().addAll("nav-control", "nav-item");
        if (text.equals(activeNav)) {
            item.getStyleClass().add("nav-active");
        }
        item.setAlignment(Pos.CENTER_LEFT);
        item.setOnMouseClicked(event -> action.run());
        item.setCursor(Cursor.HAND);
        return item;
    }

    // 构建主界面顶部状态栏
    HBox mainTopbar() {
        HBox topbar = new HBox(14);
        topbar.getStyleClass().add("content-topbar");
        topbar.setAlignment(Pos.CENTER_RIGHT);
        Button theme = ghostTextButton("主题");
        theme.setOnAction(event -> showSettingsPage());
        Label name = titleLabel(displayName(), 14);
        topbar.getChildren().addAll(spacer(), theme, new Separator(Orientation.VERTICAL),
                avatar(displayInitial(), "#c8d1dc", 34, profile == null ? "" : profile.avatar()), name);
        return topbar;
    }

    // 构建底部传输状态栏
    HBox statusFooter() {
        HBox footer = new HBox(18);
        footer.getStyleClass().add("status-footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        Button changePath = ghostTextButton("更改");
        changePath.setOnAction(event -> showSettingsPage());
        footer.getChildren().addAll(mutedLabel("传输模式： " + transferMode(), 14), separatorVertical(),
                mutedLabel("当前网速：", 14), accentLabel(currentSpeed(), 14),
                spacer(), mutedLabel("存储位置： " + currentSettings.receiveDir(), 14), changePath);
        return footer;
    }

    // 返回当前传输模式文案
    private String transferMode() {
        return currentSettings.groupCode().isBlank() ? "公开局域网" : "口令组";
    }

    // 返回当前正在传输任务的总速度
    private String currentSpeed() {
        if (currentSummary == null || currentSummary.tasks().isEmpty()) {
            return "0 B/s";
        }
        double total = currentSummary.tasks().stream()
                .filter(task -> "传输中".equals(task.status()))
                .mapToDouble(task -> speedBytes(task.speed()))
                .sum();
        return speedText(total);
    }

    // 把速度文案转成字节每秒
    private double speedBytes(String value) {
        String text = value.trim().replace("/s", "").trim();
        String[] parts = text.split(" ");
        double number = Double.parseDouble(parts[0]);
        if ("GB".equals(parts[1])) {
            return number * 1024 * 1024 * 1024;
        }
        if ("MB".equals(parts[1])) {
            return number * 1024 * 1024;
        }
        if ("KB".equals(parts[1])) {
            return number * 1024;
        }
        return number;
    }

    // 把字节每秒转成速度文案
    private String speedText(double bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.ROOT, "%.2f MB/s", bytes / 1024 / 1024);
        }
        if (bytes >= 1024) {
            return String.format(Locale.ROOT, "%.2f KB/s", bytes / 1024);
        }
        return String.format(Locale.ROOT, "%.0f B/s", bytes);
    }

    // 构建用户或近期对象卡片
    Node userCard(UserDevice device, boolean large) {
        return userCard(device, large, false);
    }

    // 构建用户或近期对象卡片，可在建组选择模式隐藏发送按钮
    Node userCard(UserDevice device, boolean large, boolean pickMode) {
        HBox card = new HBox(large ? 14 : 8);
        card.getStyleClass().add(large ? "user-card-large" : "user-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        VBox text = new VBox(large ? 6 : 4, titleLabel(userCardTitle(device), large ? 18 : 14),
                mutedLabel(userCardSubTitle(device), large ? 14 : 11));
        if (!device.groupTarget()) {
            text.getChildren().add(statusLine(device.status(), large ? "上次在线： " + device.lastSeen() : device.lastSeen(), large ? 13 : 11));
        }
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        card.getChildren().addAll(avatar(device.avatarText(), device.color(), large ? 44 : 34, device.avatar()), text);
        if (selectedTargets.contains(device)) {
            card.getStyleClass().add("selected-target");
        }
        card.setCursor(Cursor.HAND);
        if (large && !pickMode) {
            Button add = iconToggleButton("mdi2s-send", "发送", false);
            add.setOnAction(event -> {
                addRecentTarget(device);
                toast("已添加到近期传输对象并选中");
                showFileTransferPage();
            });
            HBox actions = new HBox(8, add);
            actions.setAlignment(Pos.CENTER_RIGHT);
            card.getChildren().add(actions);
        } else if (!large) {
            Button remove = compactButton("-");
            remove.setTooltip(new Tooltip("从近期传输对象移除"));
            remove.setOnAction(event -> {
                recentTargets.remove(device);
                selectedTargets.remove(device);
                showFileTransferPage();
                event.consume();
            });
            card.getChildren().add(remove);
            card.setOnMouseClicked(event -> {
                if (selectedTargets.remove(device)) {
                    card.getStyleClass().remove("selected-target");
                } else {
                    selectedTargets.add(device);
                    card.getStyleClass().add("selected-target");
                }
            });
        }
        return card;
    }

    // 返回用户卡片第一行标题
    private String userCardTitle(UserDevice device) {
        if (device.groupTarget()) {
            return device.nickname();
        }
        String local = isSelfDevice(device) ? "(本机)" : "";
        return local + device.deviceName() + " | " + device.nickname();
    }

    // 返回用户卡片第二行说明
    private String userCardSubTitle(UserDevice device) {
        if (device.groupTarget()) {
            return device.deviceName();
        }
        return device.signature();
    }

    // 判断卡片设备是否为当前登录本机
    private boolean isSelfDevice(UserDevice device) {
        return profile != null && profile.userId().equals(device.id());
    }

    // 把用户加入近期传输对象队列
    void addRecentTarget(UserDevice device) {
        recentTargetsLoaded = true;
        recentTargets.remove(device);
        recentTargets.add(device);
        selectedTargets.remove(device);
        selectedTargets.add(device);
    }

    // 向传输表格加入一行任务
    void addTransferRow(GridPane table, int row, TransferTask task) {
        table.add(fileNameCell(task.fileName()), 0, row);
        table.add(mutedLabel(task.target().nickname() + " (" + task.target().deviceName() + ")", 14), 1, row);
        table.add(progressCell(task.progressPercent()), 2, row);
        table.add(mutedLabel(task.size(), 14), 3, row);
        table.add(mutedLabel(task.speed(), 14), 4, row);
        table.add(mutedLabel(task.elapsed(), 14), 5, row);
        table.add(statusBadge(task.status()), 6, row);
        table.add(operationCell(task.status()), 7, row);
    }

    // 构建传输列表表格骨架
    GridPane tableGrid(String... headers) {
        GridPane table = new GridPane();
        table.getStyleClass().add("dark-table");
        table.setHgap(0);
        table.setVgap(0);
        table.setMaxWidth(Double.MAX_VALUE);
        for (int i = 0; i < headers.length; i++) {
            table.getColumnConstraints().add(column(i == 0 ? 210 : 132, true));
            Label header = mutedLabel(headers[i], 13);
            header.getStyleClass().add("table-header");
            table.add(header, i, 0);
        }
        return table;
    }

    // 构建区域标题行
    HBox sectionHeader(String title, String count) {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().add(titleLabel(title, 18));
        if (count != null) {
            header.getChildren().add(tabPill(count, null, true));
        }
        return header;
    }

    // 构建标题文字标签
    Label titleLabel(String text, int size) {
        Label label = new Label(text);
        label.getStyleClass().add("title-label");
        label.setStyle("-fx-font-size: " + compactFontSize(size) + "px; -fx-font-weight: 700;");
        return label;
    }

    // 构建弱化说明文字标签
    Label mutedLabel(String text, int size) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-label");
        label.setStyle("-fx-font-size: " + compactFontSize(size) + "px;");
        return label;
    }

    // 构建重点色文字标签
    Label accentLabel(String text, int size) {
        Label label = new Label(text);
        label.getStyleClass().add("accent-label");
        label.setStyle("-fx-font-size: " + compactFontSize(size) + "px; -fx-font-weight: 700;");
        return label;
    }

    // 根据界面压缩比例调整字号
    int compactFontSize(int size) {
        if (size >= 34) {
            return size - 7;
        }
        if (size >= 28) {
            return size - 5;
        }
        if (size >= 22) {
            return size - 4;
        }
        if (size >= 18) {
            return size - 3;
        }
        if (size >= 15) {
            return size - 2;
        }
        return size;
    }

    // 构建统一样式文本输入框
    TextField textField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("dark-input");
        return field;
    }

    // 构建统一样式密码输入框
    PasswordField passwordField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.getStyleClass().add("dark-input");
        return field;
    }

    // 构建带标签的输入项
    Node labeledField(String label, TextField field) {
        VBox box = new VBox(8, mutedLabel(label, 16), field);
        field.setPadding(new Insets(0, 14, 0, 14));
        return box;
    }

    // 构建重点操作按钮
    Button primaryButton(String text) {
        // 构建统一文本按钮基础样式
        return textButton(text, "primary-button");
    }

    // 构建次级操作按钮
    Button secondaryButton(String text) {
        // 构建统一文本按钮基础样式
        return textButton(text, "secondary-button");
    }

    // 构建描边操作按钮
    Button outlineButton(String text) {
        // 构建统一文本按钮基础样式
        return textButton(text, "outline-button");
    }

    // 构建弱化文本按钮
    Button ghostTextButton(String text) {
        // 构建统一文本按钮基础样式
        return textButton(text, "ghost-button");
    }

    // 构建小型按钮
    Button compactButton(String text) {
        // 构建统一文本按钮基础样式
        return textButton(text, "compact-button");
    }

    // 构建图标切换按钮
    Button iconToggleButton(String iconLiteral, String tooltip, boolean active) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("button-font-icon");
        icon.setIconSize(18);
        Button button = textButton("", active ? "outline-button" : "secondary-button");
        button.getStyleClass().add("icon-toggle-button");
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    // 构建统一文本按钮基础样式
    Button textButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("text-button", styleClass);
        button.setCursor(Cursor.HAND);
        return button;
    }

    // 构建窗口控制图标按钮
    Button windowButton(String iconLiteral, String tooltip) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("window-button-icon");
        icon.setIconSize(13);
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().addAll("icon-action", "window-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setCursor(Cursor.HAND);
        return button;
    }

    // 构建页签胶囊标签
    Label tabPill(String text, String count, boolean active) {
        Label pill = new Label(count == null ? text : text + "  " + count);
        pill.getStyleClass().addAll("ui-chip", active ? "ui-chip-active" : "ui-chip-muted", active ? "tab-pill-active" : "tab-pill");
        return pill;
    }

    // 构建页面内容分区容器
    VBox glassSection(String title) {
        VBox box = new VBox(6);
        box.getStyleClass().add("glass-section");
        if (title != null && !title.isBlank()) {
            box.getChildren().add(titleLabel(title, 17));
            box.getChildren().add(separator());
        }
        return box;
    }

    // 构建自动撑开的空白区域
    Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    // 构建水平分隔线
    Separator separator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("soft-separator");
        return separator;
    }

    // 构建垂直分隔线
    Separator separatorVertical() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().add("soft-separator");
        return separator;
    }

    // 构建登录页分隔短线
    Region line() {
        Region line = new Region();
        line.getStyleClass().add("soft-line");
        HBox.setHgrow(line, Priority.ALWAYS);
        return line;
    }

    // 构建登录页品牌图标
    Node createPlaneLogo(double size) {
        StackPane logo = new StackPane();
        logo.setMinSize(size, size);
        logo.setMaxSize(size, size);
        Polygon plane = new Polygon(size * 0.14, size * 0.42, size * 0.88, size * 0.10, size * 0.62, size * 0.88,
                size * 0.49, size * 0.57, size * 0.22, size * 0.55);
        plane.getStyleClass().add("plane-shape");
        logo.getChildren().add(plane);
        return logo;
    }

    // 构建注册审核提示图标
    Node reviewIllustration() {
        StackPane illustration = new StackPane();
        illustration.setMinSize(180, 160);
        Label clipboard = new Label("▤");
        clipboard.getStyleClass().add("illustration-icon");
        Label check = new Label("✓");
        check.getStyleClass().add("illustration-check");
        StackPane.setAlignment(check, Pos.BOTTOM_LEFT);
        illustration.getChildren().addAll(clipboard, check);
        return illustration;
    }

    // 构建扫描中的小进度指示器
    ProgressBar smallSpinner() {
        ProgressBar spinner = new ProgressBar();
        spinner.getStyleClass().add("small-spinner");
        spinner.setProgress(-1);
        return spinner;
    }

    // 构建设备在线状态行
    HBox statusLine(DeviceStatus status, String suffix) {
        // 构建设备在线状态行
        return statusLine(status, suffix, 13);
    }

    // 构建设备在线状态行
    HBox statusLine(DeviceStatus status, String suffix, int size) {
        HBox line = new HBox(size <= 11 ? 4 : 8);
        line.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("●");
        dot.getStyleClass().add(status == DeviceStatus.ONLINE ? "online-dot" : "offline-dot");
        Label text = new Label(status == DeviceStatus.ONLINE ? "在线" : "离线");
        text.getStyleClass().add(status == DeviceStatus.ONLINE ? "online-text" : "muted-label");
        text.setStyle("-fx-font-size: " + compactFontSize(size) + "px;");
        line.getChildren().addAll(dot, text, mutedLabel(suffix, size));
        return line;
    }

    // 构建用户头像节点
    Node avatar(String text, String color, double size) {
        return avatar(text, color, size, "");
    }

    // 构建用户图片或首字头像节点
    Node avatar(String text, String color, double size, String imageData) {
        if (imageData != null && !imageData.isBlank()) {
            try {
                return imageAvatar(imageData, size);
            } catch (IllegalArgumentException ignored) {
            }
        }
        StackPane avatar = new StackPane(new Label(text));
        avatar.getStyleClass().add("avatar");
        avatar.setStyle("-avatar-color: " + color + ";");
        avatar.setMinSize(size, size);
        avatar.setMaxSize(size, size);
        return avatar;
    }

    // 构建图片头像节点
    private Node imageAvatar(String imageData, double size) {
        byte[] bytes = Base64.getDecoder().decode(imageData);
        ImageView view = new ImageView(new Image(new ByteArrayInputStream(bytes), size, size, true, true));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setClip(new Circle(size / 2, size / 2, size / 2));
        StackPane avatar = new StackPane(view);
        avatar.getStyleClass().add("avatar");
        avatar.setStyle("-avatar-color: transparent;");
        avatar.setMinSize(size, size);
        avatar.setMaxSize(size, size);
        return avatar;
    }

    // 构建当前连接状态信息
    Node connectionInfo() {
        VBox box = new VBox(8);
        box.getStyleClass().add("connection-info");
        box.getChildren().addAll(statusLine(userDeviceStatus(), userStatusText()),
                mutedLabel("本机： " + profile.deviceName(), 13),
                mutedLabel("IP： " + currentSettings.ipv4(), 13));
        return box;
    }

    // 把当前用户状态转成底部状态点
    private DeviceStatus userDeviceStatus() {
        return profile.status() == UserStatus.OFFLINE || profile.status() == UserStatus.INVISIBLE ? DeviceStatus.OFFLINE : DeviceStatus.ONLINE;
    }

    // 返回当前用户状态文案
    private String userStatusText() {
        return switch (profile.status()) {
            case ONLINE -> "允许接收";
            case BUSY -> "忙碌确认";
            case INVISIBLE -> "隐身";
            case OFFLINE -> "暂停传输";
            case DEFAULT -> "已连接";
        };
    }

    // 构建提示胶囊信息
    Node noticePill(String icon, String text) {
        HBox pill = new HBox(12, accentLabel(icon, 19), mutedLabel(text, 16));
        pill.getStyleClass().add("notice-pill");
        pill.setAlignment(Pos.CENTER);
        return pill;
    }

    // 构建统计卡片
    Node statCard(String label, String value, String color, String icon) {
        HBox card = new HBox(18);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(avatar(icon, color, 50), new VBox(8, mutedLabel(label, 15), titleLabel(value, 28)));
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    // 构建状态选择卡片
    Node statusCard(String title, String description, String color, boolean active) {
        HBox card = new HBox(12);
        card.getStyleClass().add(active ? "status-card-active" : "status-card");
        card.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("●");
        dot.setTextFill(Color.web(color));
        card.getChildren().addAll(dot, new VBox(6, titleLabel(title, 18), mutedLabel(description, 13)));
        return card;
    }

    // 构建传输列表文件名单元格
    Node fileNameCell(String name) {
        // 构建标题文字标签
        return titleLabel(name, 14);
    }

    // 构建传输进度单元格
    Node progressCell(int percent) {
        HBox cell = new HBox(8);
        cell.setAlignment(Pos.CENTER_LEFT);
        Label value = mutedLabel(percent == 0 ? "—" : percent + "%", 14);
        ProgressBar progress = new ProgressBar(percent / 100.0);
        progress.getStyleClass().add("table-progress");
        progress.setPrefWidth(110);
        cell.getChildren().addAll(value, progress);
        return cell;
    }

    // 构建传输列表操作单元格
    Node operationCell(String status) {
        HBox cell = new HBox(8);
        cell.setAlignment(Pos.CENTER_LEFT);
        if ("传输中".equals(status)) {
            cell.getChildren().addAll(compactButton("暂停"), compactButton("取消"));
        } else if ("已完成".equals(status)) {
            cell.getChildren().add(compactButton("打开"));
        } else {
            cell.getChildren().addAll(compactButton("重试"), compactButton("移除"));
        }
        return cell;
    }

    // 构建传输状态徽标
    Label statusBadge(String status) {
        Label badge = new Label(status);
        badge.getStyleClass().add("status-badge");
        if (status.contains("完成")) {
            badge.getStyleClass().add("badge-success");
        } else if (status.contains("失败")) {
            badge.getStyleClass().add("badge-danger");
        } else {
            badge.getStyleClass().add("badge-warning");
        }
        return badge;
    }

    // 构建单行传输日志
    Node logLine(String line) {
        Text text = new Text(line);
        text.getStyleClass().add("log-text");
        if (line.contains("✓")) {
            text.getStyleClass().add("log-success");
        } else if (line.contains("×")) {
            text.getStyleClass().add("log-danger");
        } else if (line.contains("⚠")) {
            text.getStyleClass().add("log-warning");
        }
        return new TextFlow(text);
    }

    // 构建搜索输入框
    TextField searchField(String prompt) {
        TextField field = textField(prompt);
        field.setMaxWidth(290);
        return field;
    }

    // 构建统一样式复选框
    CheckBox checkBox(String text, boolean selected) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setSelected(selected);
        checkBox.getStyleClass().add("dark-check");
        return checkBox;
    }

    // 构建 IP 地址展示列
    HBox ipColumn(String title, String value) {
        FontIcon copy = new FontIcon("mdi2c-content-copy");
        copy.getStyleClass().add("button-font-icon");
        copy.setIconSize(15);
        Button copyButton = compactButton("");
        copyButton.setGraphic(copy);
        copyButton.setTooltip(new Tooltip("复制"));
        copyButton.setOnAction(event -> copyToClipboard(value, "已复制" + title));
        fixedWidth(copyButton, 32);
        HBox row = new HBox(10, new VBox(4, mutedLabel(title, 14), titleLabel(value, 17)), copyButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // 构建速度限制输入行
    HBox limitRow(String title, int value) {
        TextField field = textField(String.valueOf(value));
        fixedWidth(field, 90);
        return new HBox(10, mutedLabel(title, 15), field, mutedLabel("MB/s", 14));
    }

    // 构建主题色块按钮
    StackPane colorSwatch(String color, boolean selected) {
        StackPane swatch = new StackPane();
        swatch.getStyleClass().add(selected ? "color-swatch-selected" : "color-swatch");
        swatch.setStyle("-swatch-color: " + color + ";");
        if (selected) {
            swatch.getChildren().add(new Label("✓"));
        }
        swatch.setCursor(Cursor.HAND);
        return swatch;
    }

    // 构建下拉选择框
    ComboBox<String> comboBox(String value) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().add(value);
        combo.setValue(value);
        return combo;
    }

    // 构建网格列约束
    ColumnConstraints column(double width, boolean grow) {
        ColumnConstraints column = new ColumnConstraints();
        column.setMinWidth(0);
        column.setPrefWidth(width);
        if (grow) {
            column.setHgrow(Priority.ALWAYS);
        }
        return column;
    }

    // 向资料表单加入一行字段
    void addProfileRow(GridPane grid, int row, String label, String value, String counter, String action) {
        grid.add(mutedLabel(label, 14), 0, row);
        Label field = titleLabel(value, 16);
        StackPane cell = new StackPane(field);
        if (counter != null) {
            cell.getChildren().add(counterLabel(counter));
        }
        grid.add(cell, 1, row);
        Button button = secondaryButton(action);
        if ("复制".equals(action)) {
            button.setOnAction(event -> copyToClipboard(value, "已复制" + label));
        }
        grid.add(button, 2, row);
    }

    // 构建字数统计标签
    Label counterLabel(String counter) {
        Label label = mutedLabel(counter, 12);
        StackPane.setAlignment(label, Pos.CENTER_RIGHT);
        return label;
    }

    // 向信息表格加入一行内容
    void addInfoRow(GridPane grid, int row, String label, String value) {
        grid.add(mutedLabel(label, 14), 0, row);
        grid.add(titleLabel(value, 16), 1, row);
    }

    // 给控件设置固定宽度
    Node fixedWidth(Region node, double width) {
        node.setMinWidth(width);
        node.setPrefWidth(width);
        node.setMaxWidth(width);
        return node;
    }

    // 获取当前显示昵称
    String displayName() {
        return profile == null ? "admin" : profile.nickname();
    }

    // 获取当前头像首字
    String displayInitial() {
        // 从名称中提取头像首字
        return initialOf(displayName());
    }

    // 从名称中提取头像首字
    String initialOf(String name) {
        if (name == null || name.isBlank()) {
            return "A";
        }
        return name.substring(0, 1).toUpperCase();
    }

    // 为自定义标题栏启用窗口拖动
    void enableDrag(Node node) {
        node.setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX();
            dragOffsetY = event.getSceneY();
        });
        node.setOnMouseDragged(event -> {
            if (!stage.isMaximized()) {
                stage.setX(event.getScreenX() - dragOffsetX);
                stage.setY(event.getScreenY() - dragOffsetY);
            }
        });
    }

    // 复制文本到系统剪贴板
    void copyToClipboard(String value, String message) {
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
        toast(message);
    }

    // 播放传输完成提示音
    void playDoneSound() {
        if (currentSettings == null || !currentSettings.soundOnComplete()) {
            return;
        }
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception ignored) {
        }
    }

    // 校验用户输入的传输口令
    private boolean codeMatches(String code, String codeHash) {
        return codeHash == null || codeHash.isBlank() || codeHash.equalsIgnoreCase(codeHash(code));
    }

    // 计算用户输入口令的 SHA-256
    private String codeHash(String code) {
        String value = code == null ? "" : code.trim();
        if (value.isBlank()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ignored) {
            return "";
        }
    }

    // 显示临时提示消息
    void toast(String message) {
        if (stage.getScene() == null || !(stage.getScene().getRoot() instanceof StackPane root)) {
            return;
        }
        Label label = new Label(message);
        label.getStyleClass().add("toast");
        root.getChildren().add(label);
        StackPane.setAlignment(label, Pos.BOTTOM_CENTER);
        StackPane.setMargin(label, new Insets(0, 0, 28, 0));
        new Timeline(new KeyFrame(Duration.seconds(2), event -> root.getChildren().remove(label))).play();
    }
}
