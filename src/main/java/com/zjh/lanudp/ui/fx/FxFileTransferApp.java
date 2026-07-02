package com.zjh.lanudp.ui.fx;

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
import javafx.scene.control.Button;
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
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FxFileTransferApp extends Application {
    private static final String APP_TITLE = "极速互传 v1.0.0";
    private static final String ACCENT_ORANGE = "#ff8500";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final double STARTUP_WIDTH = 860;
    private static final double STARTUP_HEIGHT = 600;
    private static final double AUTH_WIDTH = 780;
    private static final double AUTH_HEIGHT = 700;
    private static final double MAIN_WIDTH = 1240;
    private static final double MAIN_HEIGHT = 820;
    private static final double MAIN_MIN_WIDTH = 1040;
    private static final double MAIN_MIN_HEIGHT = 640;

    private final BackendFacade backend = new MockBackendFacade();
    private final ObservableList<TransferFile> pendingFiles = FXCollections.observableArrayList();
    private final List<UserDevice> recentTargets = new ArrayList<>();
    private final List<UserDevice> selectedTargets = new ArrayList<>();

    private Stage stage;
    private Profile profile;
    private TransferSummary currentSummary;
    private String accentColor = ACCENT_ORANGE;
    private boolean userListGridView;
    private boolean recentTargetsLoaded;
    private int userListPage;
    private double requestedWindowWidth = -1;
    private double requestedWindowHeight = -1;
    private double dragOffsetX;
    private double dragOffsetY;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle(APP_TITLE);
        showAuth(false);
    }

    private void showAuth(boolean registerMode) {
        VBox body = new VBox(24);
        body.getStyleClass().add("auth-body");
        body.setAlignment(Pos.CENTER);

        HBox brand = new HBox(18, createPlaneLogo(58), new VBox(4,
                titleLabel("极速互传", 30),
                mutedLabel("极速传输，安全高效", 16)
        ));
        brand.setAlignment(Pos.CENTER);

        Node form = registerMode ? registerForm() : loginForm();
        body.getChildren().addAll(brand, form);
        setWindow(windowShell(body), AUTH_WIDTH, AUTH_HEIGHT, AUTH_WIDTH, AUTH_HEIGHT);
    }

    private Node loginForm() {
        VBox form = new VBox(14);
        form.getStyleClass().add("auth-form");
        TextField account = textField("请输入账号");
        PasswordField password = passwordField("请输入密码");
        account.setText("admin");
        password.setText("admin");
        CheckBox rememberMe = checkBox("记住我", false);

        Button loginButton = primaryButton("登录");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(event -> backend.login(new LoginRequest(account.getText().trim(), password.getText(), rememberMe.isSelected()))
                .thenAccept(result -> Platform.runLater(() -> {
                    if (result.success()) {
                        profile = result.profile();
                        showFileTransferPage();
                    } else {
                        toast(result.message());
                    }
                })));

        Button registerButton = outlineButton("注册账号");
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setOnAction(event -> showAuth(true));
        HBox divider = new HBox(18, line(), mutedLabel("或", 14), line());
        divider.setAlignment(Pos.CENTER);
        form.getChildren().addAll(labeledField("账号", account), labeledField("密码", password), rememberMe, loginButton, divider, registerButton);
        return form;
    }

    private Node registerForm() {
        VBox form = new VBox(14);
        form.getStyleClass().add("auth-form");
        TextField account = textField("请输入账号");
        PasswordField password = passwordField("请输入密码");
        TextField device = textField("当前设备名称");
        Button submit = primaryButton("提交注册申请");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setOnAction(event -> backend.register(new RegisterRequest(account.getText().trim(), password.getText(), device.getText().trim()))
                .thenAccept(result -> Platform.runLater(() -> {
                    profile = result.profile();
                    showReviewPending();
                })));
        Button backLogin = outlineButton("已有账号，返回登录");
        backLogin.setMaxWidth(Double.MAX_VALUE);
        backLogin.setOnAction(event -> showAuth(false));
        form.getChildren().addAll(labeledField("账号", account), labeledField("密码", password), labeledField("设备名称", device), submit, backLogin);
        return form;
    }

    private void showReviewPending() {
        VBox page = new VBox(20);
        page.getStyleClass().add("page-content");
        HBox breadcrumb = new HBox(16, secondaryButton("返回"), new Separator(Orientation.VERTICAL), accentLabel("注册审核提示", 16));
        breadcrumb.setAlignment(Pos.CENTER_LEFT);
        ((Button) breadcrumb.getChildren().get(0)).setOnAction(event -> showAuth(false));

        VBox card = new VBox(22);
        card.getStyleClass().add("pending-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(82, 44, 70, 44));
        Button ok = primaryButton("我知道了");
        Button back = secondaryButton("返回登录");
        ok.setOnAction(event -> showAuth(false));
        back.setOnAction(event -> showAuth(false));
        card.getChildren().addAll(
                reviewIllustration(),
                titleLabel("注册申请已提交", 30),
                mutedLabel("您的账号正在等待管理员审核，通过后即可登录使用", 18),
                noticePill("◷", "审核通常会在 1 个工作日 内完成，请耐心等待。"),
                fixedWidth(ok, 328),
                fixedWidth(back, 328)
        );
        page.getChildren().addAll(breadcrumb, card);
        setWindow(mainWindowShell("文件传输", page, false, false), MAIN_WIDTH, MAIN_HEIGHT, MAIN_MIN_WIDTH, MAIN_MIN_HEIGHT);
    }

    private void showFileTransferPage() {
        backend.loadRecentDevices().thenAccept(devices -> Platform.runLater(() -> {
            if (!recentTargetsLoaded) {
                recentTargets.addAll(devices.subList(0, Math.min(5, devices.size())));
                recentTargetsLoaded = true;
            }
            VBox page = new VBox(8);
            page.getStyleClass().add("page-content");
            page.getChildren().addAll(uploadStrip(), recentTargetsSection(recentTargets), transferListSection(sampleTransferTasks()));
            setWindow(mainWindowShell("文件传输", page, true, true), MAIN_WIDTH, MAIN_HEIGHT, MAIN_MIN_WIDTH, MAIN_MIN_HEIGHT);
        }));
    }

    private void showTransferResultPage() {
        if (currentSummary == null) {
            startTransfer();
            return;
        }
        VBox page = new VBox(8);
        page.getStyleClass().add("page-content");
        page.getChildren().addAll(uploadStrip(), resultSummarySection(currentSummary), transferLogSection(currentSummary));
        setWindow(mainWindowShell("文件传输", page, true, true), MAIN_WIDTH, MAIN_HEIGHT, MAIN_MIN_WIDTH, MAIN_MIN_HEIGHT);
    }

    private void showUserListPage() {
        backend.loadAllDevices().thenAccept(devices -> Platform.runLater(() -> {
            VBox page = new VBox(8);
            page.getStyleClass().add("page-content");

            TextField search = searchField("搜索用户昵称或设备 ID");
            Button scan = primaryButton("扫描用户");
            scan.setOnAction(event -> showScanPage());
            Label lastScan = mutedLabel("上次扫描： 刚刚", 14);
            HBox scanLine = new HBox(12, search, scan, lastScan);
            scanLine.setAlignment(Pos.CENTER_LEFT);
            StackPane scanHeader = new StackPane(scanLine);
            scanHeader.setAlignment(Pos.CENTER_LEFT);
            scanHeader.setMinHeight(46);
            scanHeader.setMaxWidth(Double.MAX_VALUE);

            Button listView = userListGridView ? secondaryButton("列表形") : outlineButton("列表形");
            listView.setOnAction(event -> {
                userListGridView = false;
                userListPage = 0;
                showUserListPage();
            });
            Button gridView = userListGridView ? outlineButton("矩阵形") : secondaryButton("矩阵形");
            gridView.setOnAction(event -> {
                userListGridView = true;
                userListPage = 0;
                showUserListPage();
            });
            HBox totalLine = new HBox(12, mutedLabel("共 " + devices.size() + " 个用户", 16), new HBox(8, listView, gridView));
            totalLine.setAlignment(Pos.CENTER_LEFT);

            if (userListGridView) {
                page.getChildren().addAll(scanHeader, separator(), totalLine, userGrid(devices));
            } else {
                VBox list = new VBox(10);
                list.setMaxWidth(Double.MAX_VALUE);
                devices.forEach(device -> list.getChildren().add(userCard(device, true)));
                page.getChildren().addAll(scanHeader, separator(), totalLine, list);
            }
            setWindow(mainWindowShell("用户列表", page, true, true), MAIN_WIDTH, MAIN_HEIGHT, MAIN_MIN_WIDTH, MAIN_MIN_HEIGHT);
        }));
    }

    private Node userGrid(List<UserDevice> devices) {
        int pageSize = 15;
        int maxPage = Math.max(0, (devices.size() - 1) / pageSize);
        userListPage = Math.min(userListPage, maxPage);
        int from = userListPage * pageSize;
        int to = Math.min(devices.size(), from + pageSize);

        GridPane grid = cardGrid(3, 14, 12);
        for (int i = from; i < to; i++) {
            addCard(grid, userCard(devices.get(i), true), i - from, 3);
        }

        Button previous = secondaryButton("上一页");
        previous.setDisable(userListPage == 0);
        previous.setOnAction(event -> {
            userListPage--;
            showUserListPage();
        });
        Button next = secondaryButton("下一页");
        next.setDisable(userListPage >= maxPage);
        next.setOnAction(event -> {
            userListPage++;
            showUserListPage();
        });
        HBox pages = new HBox(12, previous, mutedLabel((userListPage + 1) + " / " + (maxPage + 1), 14), next);
        pages.setAlignment(Pos.CENTER);
        return new VBox(18, grid, pages);
    }

    private GridPane cardGrid(int columns, double hgap, double vgap) {
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

    private void addCard(GridPane grid, Node card, int index, int columns) {
        GridPane.setHgrow(card, Priority.ALWAYS);
        GridPane.setFillWidth(card, true);
        grid.add(card, index % columns, index / columns);
    }

    private void showScanPage() {
        backend.scanLanDevices().thenAccept(devices -> Platform.runLater(() -> {
            VBox page = new VBox(22);
            page.getStyleClass().add("scan-page");
            page.setAlignment(Pos.CENTER);
            Button cancel = outlineButton("取消扫描");
            cancel.setOnAction(event -> showUserListPage());
            HBox scanning = new HBox(12, smallSpinner(), mutedLabel("正在扫描中，请稍候...", 18));
            scanning.setAlignment(Pos.CENTER);
            page.getChildren().addAll(titleLabel("正在扫描局域网用户...", 34), mutedLabel("发现附近设备，建立连接通道", 18),
                    radar(devices), scanning, noticePill("◌", "提示：隐身用户无法被扫描发现"), fixedWidth(cancel, 160));
            setWindow(mainWindowShell("扫描局域网", page, false, true), MAIN_WIDTH, MAIN_HEIGHT, MAIN_MIN_WIDTH, MAIN_MIN_HEIGHT);
        }));
    }

    private void showProfilePage() {
        if (profile == null) {
            showAuth(false);
            return;
        }
        VBox page = new VBox(14);
        page.getStyleClass().add("page-content");
        VBox profileSection = glassSection("我的资料");
        profileSection.getChildren().add(profileEditor(profile));
        VBox statusSection = glassSection("状态设置");
        statusSection.getChildren().add(statusCards());
        statusSection.getChildren().add(customStatusField());
        VBox moreSection = glassSection("更多信息");
        moreSection.getChildren().add(moreInfo(profile));
        HBox actions = new HBox(20, fixedWidth(primaryButton("保存"), 164), fixedWidth(secondaryButton("重置"), 164));
        actions.setAlignment(Pos.CENTER);
        page.getChildren().addAll(profileSection, statusSection, moreSection, actions);
        setWindow(mainWindowShell("我的", page, true, true), MAIN_WIDTH, MAIN_HEIGHT, MAIN_MIN_WIDTH, MAIN_MIN_HEIGHT);
    }

    private void showSettingsPage() {
        VBox page = new VBox(14);
        page.getStyleClass().add("page-content");
        VBox section = glassSection("系统设置");
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
        setWindow(mainWindowShell("系统设置", page, true, true), MAIN_WIDTH, MAIN_HEIGHT, MAIN_MIN_WIDTH, MAIN_MIN_HEIGHT);
    }

    private Parent windowShell(Node body) {
        VBox shell = new VBox();
        shell.getStyleClass().add("window-shell");
        shell.setMinSize(0, 0);
        shell.getChildren().addAll(titleBar(), body);
        VBox.setVgrow(body, Priority.ALWAYS);
        return appRoot(shell);
    }

    private Parent mainWindowShell(String activeNav, Node page, boolean topActions, boolean footer) {
        VBox shell = new VBox();
        shell.getStyleClass().add("window-shell");
        shell.setMinSize(0, 0);
        shell.getChildren().add(titleBar());

        HBox main = new HBox();
        main.getStyleClass().add("main-area");
        main.getChildren().add(sidebar(activeNav, "扫描局域网".equals(activeNav)));

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
        return appRoot(shell);
    }

    private Parent appRoot(Node shell) {
        StackPane root = new StackPane(shell);
        root.getStyleClass().add("app-root");
        root.setStyle("-color-accent: " + accentColor + ";");
        if (shell instanceof Region region) {
            region.setMinSize(0, 0);
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            clipToRoundedWindow(region);
        }
        return root;
    }

    private void clipToRoundedWindow(Region region) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        region.setClip(clip);
    }

    private void setWindow(Parent root, double width, double height, double minWidth, double minHeight) {
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
        scene.getStylesheets().add(Objects.requireNonNull(FxFileTransferApp.class.getResource("app.css")).toExternalForm());
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
    }

    private HBox titleBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("title-bar");
        bar.setMinSize(0, 0);
        bar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(APP_TITLE);
        title.getStyleClass().add("window-title");
        Button minimize = windowButton("mdi2w-window-minimize", "最小化");
        minimize.setOnAction(event -> stage.setIconified(true));
        Button close = windowButton("mdi2w-window-close", "关闭");
        close.setOnAction(event -> stage.close());
        bar.getChildren().addAll(title, spacer(), minimize, close);
        enableDrag(bar);
        return bar;
    }

    private VBox sidebar(String activeNav, boolean includeScan) {
        VBox sidebar = new VBox(20);
        sidebar.getStyleClass().add("sidebar");
        if (includeScan) {
            sidebar.getChildren().add(navItem("扫描局域网", activeNav, this::showScanPage));
        }
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

    private Node navItem(String text, String activeNav, Runnable action) {
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

    private HBox mainTopbar() {
        HBox topbar = new HBox(14);
        topbar.getStyleClass().add("content-topbar");
        topbar.setAlignment(Pos.CENTER_RIGHT);
        Button theme = ghostTextButton("主题");
        theme.setOnAction(event -> showSettingsPage());
        topbar.getChildren().addAll(spacer(), theme, new Separator(Orientation.VERTICAL), avatar(displayInitial(), "#c8d1dc", 34), new Label(displayName()));
        return topbar;
    }

    private HBox statusFooter() {
        HBox footer = new HBox(18);
        footer.getStyleClass().add("status-footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        Button changePath = ghostTextButton("更改");
        changePath.setOnAction(event -> toast("存储位置接口已预留。"));
        footer.getChildren().addAll(mutedLabel("传输模式： 局域网直连", 14), separatorVertical(),
                mutedLabel("当前网速：", 14), accentLabel("↑ 23.6 MB/s", 14), mutedLabel("↓ 4.8 MB/s", 14),
                spacer(), mutedLabel("存储位置： D:\\极速互传\\接收文件", 14), changePath);
        return footer;
    }

    private VBox uploadStrip() {
        VBox strip = glassSection("");
        strip.getStyleClass().add("upload-strip");
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_LEFT);
        Button upload = primaryButton("上传文件");
        upload.setOnAction(event -> chooseFiles());
        Button chooseFolder = primaryButton("选择文件夹");
        chooseFolder.setOnAction(event -> chooseFolder());
        row.getChildren().addAll(upload, chooseFolder, mutedLabel(uploadHint(), 14), spacer());
        if (!pendingFiles.isEmpty()) {
            Button start = outlineButton("开始发送");
            start.setOnAction(event -> startTransfer());
            Button clear = ghostTextButton("全部清除");
            clear.setOnAction(event -> {
                pendingFiles.clear();
                showFileTransferPage();
            });
            row.getChildren().addAll(start, clear);
        }
        strip.getChildren().add(row);
        strip.setOnDragOver(event -> {
            if (event.getGestureSource() != strip && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        strip.setOnDragEntered(event -> {
            if (event.getDragboard().hasFiles()) {
                setUploadDragActive(strip, true);
            }
            event.consume();
        });
        strip.setOnDragExited(event -> {
            setUploadDragActive(strip, false);
            event.consume();
        });
        strip.setOnDragDropped(event -> {
            setUploadDragActive(strip, false);
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles()) {
                addFiles(dragboard.getFiles());
                event.setDropCompleted(true);
            }
            event.consume();
        });
        if (!pendingFiles.isEmpty()) {
            GridPane cards = cardGrid(2, 8, 8);
            cards.getStyleClass().add("pending-file-list");
            for (int i = 0; i < pendingFiles.size(); i++) {
                addCard(cards, pendingFileCard(pendingFiles.get(i)), i, 2);
            }
            strip.getChildren().add(cards);
        }
        return strip;
    }

    private String uploadHint() {
        return pendingFiles.isEmpty() ? "支持拖拽文件或文件夹到此处上传" : "已选择 " + pendingFiles.size() + " 个待传输项";
    }

    private void setUploadDragActive(VBox strip, boolean active) {
        if (active) {
            if (!strip.getStyleClass().contains("upload-strip-dragover")) {
                strip.getStyleClass().add("upload-strip-dragover");
            }
        } else {
            strip.getStyleClass().remove("upload-strip-dragover");
        }
    }

    private Node pendingFileCard(TransferFile file) {
        HBox card = new HBox(12);
        card.getStyleClass().addAll("user-card-large", "pending-file-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);

        Label name = titleLabel(file.fileName(), 15);
        name.setWrapText(true);
        name.setMaxWidth(Double.MAX_VALUE);
        VBox text = new VBox(4, name, mutedLabel(file.size(), 13), mutedLabel(modifiedAt(file.path()), 12));
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button remove = compactButton("-");
        remove.setTooltip(new Tooltip("从待传输项移除"));
        remove.setOnAction(event -> {
            pendingFiles.remove(file);
            showFileTransferPage();
        });
        card.getChildren().addAll(fileIcon(file.path()), text, remove);
        return card;
    }

    private Node fileIcon(Path path) {
        FontIcon icon = new FontIcon(iconFor(path));
        icon.getStyleClass().add("file-card-font-icon");
        icon.setIconSize(24);
        StackPane box = new StackPane(icon);
        box.getStyleClass().add("file-icon-box");
        box.setMinSize(44, 44);
        box.setMaxSize(44, 44);
        return box;
    }

    private String modifiedAt(Path path) {
        if (path == null) {
            return "修改日期：-";
        }
        try {
            return "修改日期：" + DATE_MINUTE.format(Files.getLastModifiedTime(path).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime());
        } catch (Exception ignored) {
            return "修改日期：-";
        }
    }

    private String iconFor(Path path) {
        if (path != null && Files.isDirectory(path)) {
            return folderIcon(path);
        }
        String ext = extension(path == null ? "" : path.getFileName().toString());
        return switch (ext) {
            case "pdf" -> "fltral-document-pdf-24";
            case "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg" -> "fltral-image-24";
            case "mp4", "mov", "mkv", "avi", "webm" -> "fltrmz-video-24";
            case "mp3", "wav", "flac", "aac", "ogg" -> "fltrmz-music-note-24";
            case "zip", "rar", "7z", "tar", "gz" -> "fltral-archive-24";
            case "doc", "docx", "rtf", "txt", "md" -> "fltrmz-text-description-24";
            case "xls", "xlsx", "csv" -> "fltrmz-table-24";
            case "ppt", "pptx", "key" -> "fltrmz-slide-text-24";
            case "java", "kt", "js", "ts", "jsx", "tsx", "py", "c", "cpp", "cs", "go", "rs", "html", "css", "xml", "json", "yml", "yaml" -> "fltral-code-24";
            case "prproj", "aep", "aepx" -> "fltrmz-video-clip-24";
            case "psd", "ai", "xd", "indd" -> "fltrmz-paint-brush-24";
            default -> "fltral-document-24";
        };
    }

    private String folderIcon(Path folder) {
        // ponytail: direct children only; recurse later if folder icon accuracy matters more than drag speed.
        try (DirectoryStream<Path> children = Files.newDirectoryStream(folder)) {
            for (Path child : children) {
                String name = child.getFileName().toString().toLowerCase(Locale.ROOT);
                if (isAdobeVideoProject(name)) {
                    return "fltrmz-video-clip-24";
                }
                if (isAdobeDesignProject(name)) {
                    return "fltrmz-paint-brush-24";
                }
                if (isIdeProjectMarker(name)) {
                    return "fltral-app-folder-24";
                }
            }
        } catch (Exception ignored) {
            return "fltral-folder-24";
        }
        return "fltral-folder-24";
    }

    private boolean isAdobeVideoProject(String name) {
        return name.endsWith(".prproj") || name.endsWith(".aep") || name.endsWith(".aepx");
    }

    private boolean isAdobeDesignProject(String name) {
        return name.endsWith(".psd") || name.endsWith(".ai") || name.endsWith(".xd") || name.endsWith(".indd");
    }

    private boolean isIdeProjectMarker(String name) {
        return name.equals(".idea") || name.equals(".vscode") || name.equals("pom.xml") || name.equals("build.gradle")
                || name.equals("settings.gradle") || name.equals("package.json") || name.equals("pyproject.toml")
                || name.equals("cargo.toml") || name.equals("go.mod") || name.endsWith(".sln")
                || name.endsWith(".csproj") || name.endsWith(".vcxproj") || name.endsWith(".xcodeproj");
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private VBox recentTargetsSection(List<UserDevice> devices) {
        VBox section = glassSection("近期传输对象");
        GridPane cards = cardGrid(5, 8, 8);
        for (int i = 0; i < devices.size(); i++) {
            addCard(cards, userCard(devices.get(i), false), i, 5);
        }
        section.getChildren().add(cards);
        return section;
    }

    private VBox transferListSection(List<TransferTask> tasks) {
        VBox section = glassSection("");
        HBox header = sectionHeader("传输列表", null);
        Button clearCompleted = ghostTextButton("清除已完成");
        clearCompleted.setOnAction(event -> toast("清除已完成传输记录接口已预留。"));
        header.getChildren().addAll(tabPill("全部", "4", true), tabPill("进行中", "2", false), tabPill("已完成", "1", false),
                tabPill("已失败", "1", false), spacer(), clearCompleted);
        section.getChildren().add(header);
        GridPane table = tableGrid("文件名", "目标对象", "进度", "大小", "速度", "时间", "状态", "操作");
        for (int i = 0; i < tasks.size(); i++) {
            addTransferRow(table, i + 1, tasks.get(i));
        }
        section.getChildren().add(table);
        return section;
    }

    private VBox resultSummarySection(TransferSummary summary) {
        VBox section = glassSection("传输结果");
        HBox stats = new HBox(12, statCard("目标总数", String.valueOf(summary.targetCount()), "#4f7bd8", "总"),
                statCard("成功", String.valueOf(summary.successCount()), "#2ecc40", "成"),
                statCard("失败", String.valueOf(summary.failedCount()), "#ff5353", "败"),
                statCard("重试", String.valueOf(summary.retryCount()), "#ffb22c", "重"));
        section.getChildren().addAll(stats, transferListSection(summary.tasks()));
        return section;
    }

    private VBox transferLogSection(TransferSummary summary) {
        VBox section = glassSection("传输日志");
        HBox header = new HBox(12, mutedLabel("耗时 " + summary.elapsed(), 14), spacer());
        Button clear = secondaryButton("清空日志");
        clear.setOnAction(event -> toast("清空日志接口已预留。"));
        ToggleButton autoScroll = new ToggleButton();
        autoScroll.getStyleClass().add("switch-toggle");
        header.getChildren().addAll(mutedLabel("自动滚动", 14), autoScroll, clear);
        VBox logBox = new VBox(6);
        logBox.getStyleClass().add("log-box");
        summary.logs().forEach(line -> logBox.getChildren().add(logLine(line)));
        ScrollPane logScroll = new ScrollPane(logBox);
        logScroll.getStyleClass().add("log-scroll");
        section.getChildren().addAll(header, logScroll);
        return section;
    }

    private HBox profileEditor(Profile profile) {
        HBox root = new HBox(28);
        root.setAlignment(Pos.CENTER_LEFT);
        StackPane photo = new StackPane(avatar(initialOf(profile.nickname()), "#d6dee8", 120));
        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(10);
        fields.getColumnConstraints().addAll(column(96, false), column(470, true), column(88, false));
        addProfileRow(fields, 0, "昵称", profile.nickname(), "2/20", "编辑");
        addProfileRow(fields, 1, "用户ID", profile.userId(), null, "复制");
        addProfileRow(fields, 2, "设备名称", profile.deviceName(), null, "编辑");
        addProfileRow(fields, 3, "个性签名", profile.signature(), "9/60", "编辑");
        root.getChildren().addAll(photo, fields);
        return root;
    }

    private GridPane statusCards() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getColumnConstraints().addAll(column(160, true), column(180, true), column(180, true), column(180, true), column(180, true));
        grid.add(statusCard("默认状态", "自动检测在线状态", "#6f7782", true), 0, 0);
        grid.add(statusCard("在线", "允许被发现并接收文件", "#2ecc40", false), 1, 0);
        grid.add(statusCard("忙碌", "接收前先提示确认", "#ffb22c", false), 2, 0);
        grid.add(statusCard("隐身", "不出现在扫描列表", "#8a52d8", false), 3, 0);
        grid.add(statusCard("离线", "暂停所有传输", "#9aa0a6", false), 4, 0);
        return grid;
    }

    private HBox customStatusField() {
        TextField field = textField("输入自定义状态");
        Button save = outlineButton("保存");
        save.setOnAction(event -> toast("状态设置接口已预留。"));
        HBox row = new HBox(12, field, save);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private GridPane moreInfo(Profile profile) {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(10);
        grid.getColumnConstraints().addAll(column(160, false), column(Region.USE_COMPUTED_SIZE, true), column(180, false));
        addInfoRow(grid, 0, "注册时间", DATE_TIME.format(profile.registeredAt()));
        addInfoRow(grid, 1, "最后登录", DATE_TIME.format(profile.lastLoginAt()));
        addInfoRow(grid, 2, "版本信息", profile.version());
        return grid;
    }

    private HBox ipInfo() {
        return new HBox(12, ipColumn("IPv4 地址", "192.168.1.100"), ipColumn("IPv6 地址", "fe80::1a2b:3c4d"));
    }

    private VBox speedLimitControls() {
        VBox root = new VBox(10, limitRow("上传速度限制", 10), limitRow("下载速度限制", 20));
        root.setMaxWidth(360);
        return root;
    }

    private HBox retryControls() {
        Spinner<Integer> retries = new Spinner<>(0, 10, 3);
        retries.getStyleClass().add("dark-spinner");
        fixedWidth(retries, 72);
        return new HBox(12, retries, mutedLabel("次", 15));
    }

    private VBox colorControls() {
        VBox root = new VBox(10);
        HBox swatches = new HBox(8);
        for (String color : List.of("#ff8500", "#2f80ed", "#2ecc40", "#ff5353", "#8a52d8")) {
            StackPane swatch = colorSwatch(color, color.equalsIgnoreCase(accentColor));
            swatch.setOnMouseClicked(event -> {
                accentColor = color;
                showSettingsPage();
            });
            swatches.getChildren().add(swatch);
        }
        TextField current = textField(accentColor);
        fixedWidth(current, 112);
        root.getChildren().addAll(swatches, new HBox(10, mutedLabel("自定义颜色", 15), current));
        return root;
    }

    private VBox fontControls() {
        VBox root = new VBox(10);
        HBox displayRow = new HBox(8);
        displayRow.setAlignment(Pos.CENTER_LEFT);
        displayRow.getChildren().addAll(mutedLabel("字体展示", 15), tabPill("按钮", null, true),
                tabPill("标签", null, false), fixedWidth(textField("输入框"), 90), checkBox("选项", true));
        ComboBox<String> font = comboBox("Microsoft YaHei");
        fixedWidth(font, 156);
        TextField size = textField("14");
        fixedWidth(size, 76);
        root.getChildren().addAll(displayRow, new HBox(10, mutedLabel("字体设置", 15), font, size));
        return root;
    }

    private VBox zoomControls() {
        TextField zoom = textField("100%");
        fixedWidth(zoom, 132);
        return new VBox(8, zoom);
    }

    private HBox languageControls() {
        ComboBox<String> language = comboBox(profile == null ? "简体中文" : profile.language());
        fixedWidth(language, 132);
        return new HBox(language);
    }

    private HBox startupControls() {
        return new HBox(16, checkBox("开机自启动", false), checkBox("启动后最小化到系统托盘", true), checkBox("传输完成后播放提示音", true));
    }

    private HBox settingsRow(String title, String description, Node controls) {
        VBox text = new VBox(4, titleLabel(title, 20), mutedLabel(description, 14));
        HBox row = new HBox(18);
        row.getStyleClass().add("settings-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(text, separatorVertical(), controls);
        HBox.setHgrow(text, Priority.ALWAYS);
        return row;
    }

    private StackPane radar(List<UserDevice> devices) {
        StackPane radar = new StackPane();
        radar.setMinSize(360, 260);
        radar.setMaxSize(360, 260);
        radar.getChildren().addAll(radarCircle(112), radarCircle(76), radarCircle(38));
        Line horizontal = new Line(-140, 0, 140, 0);
        Line vertical = new Line(0, -110, 0, 110);
        horizontal.getStyleClass().add("radar-axis");
        vertical.getStyleClass().add("radar-axis");
        radar.getChildren().addAll(horizontal, vertical);
        double[][] positions = {{-76, -42}, {70, -34}, {-34, 70}, {88, 58}};
        for (int i = 0; i < Math.min(devices.size(), positions.length); i++) {
            radar.getChildren().add(scanDeviceLabel(devices.get(i), positions[i][0], positions[i][1]));
        }
        return radar;
    }

    private Node userCard(UserDevice device, boolean large) {
        HBox card = new HBox(large ? 14 : 8);
        card.getStyleClass().add(large ? "user-card-large" : "user-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        VBox text = new VBox(large ? 6 : 4, titleLabel(device.nickname(), large ? 18 : 14),
                mutedLabel(device.deviceName(), large ? 14 : 11),
                statusLine(device.status(), large ? "上次在线： " + device.lastSeen() : device.lastSeen(), large ? 13 : 11));
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        card.getChildren().addAll(avatar(device.avatarText(), device.color(), large ? 44 : 34), text);
        if (selectedTargets.contains(device)) {
            card.getStyleClass().add("selected-target");
        }
        card.setCursor(Cursor.HAND);
        if (large) {
            Button add = compactButton("+");
            add.setTooltip(new Tooltip("添加到近期传输对象并选中"));
            add.setOnAction(event -> {
                addRecentTarget(device);
                toast("已添加到近期传输对象并选中");
                showUserListPage();
            });
            HBox actions = new HBox(8, add);
            actions.setAlignment(Pos.CENTER_RIGHT);
            card.getChildren().add(actions);
        } else {
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

    private void addRecentTarget(UserDevice device) {
        recentTargetsLoaded = true;
        recentTargets.remove(device);
        if (recentTargets.size() >= 5) {
            recentTargets.remove(0);
        }
        recentTargets.add(device);
        selectedTargets.remove(device);
        selectedTargets.add(device);
    }

    private List<TransferTask> sampleTransferTasks() {
        return List.of(
                new TransferTask("产品演示视频.mp4", sampleDeviceFor(0), 72, "512.00 MB", "12.35 MB/s", "00:00:32", "传输中", 0),
                new TransferTask("会议纪要.png", sampleDeviceFor(1), 36, "3.21 MB", "2.11 MB/s", "00:00:08", "传输中", 0),
                new TransferTask("用户手册.pdf", sampleDeviceFor(3), 100, "8.34 MB", "-", "2025-05-18 10:42:15", "已完成", 0),
                new TransferTask("数据备份_20250517.zip", sampleDeviceFor(2), 0, "1024.00 MB", "-", "2025-05-18 09:15:33", "传输失败", 3)
        );
    }

    private void startTransfer() {
        if (pendingFiles.isEmpty()) {
            toast("请先选择要上传的文件或文件夹");
            return;
        }
        List<UserDevice> targets = selectedTargets.isEmpty() ? new ArrayList<>(recentTargets) : new ArrayList<>(selectedTargets);
        backend.startTransfer(new ArrayList<>(pendingFiles), targets).thenAccept(summary -> Platform.runLater(() -> {
            currentSummary = summary;
            showTransferResultPage();
        }));
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要上传的文件");
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            addFiles(files);
        }
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择要上传的文件夹");
        File folder = chooser.showDialog(stage);
        if (folder != null) {
            pendingFiles.add(new TransferFile(folder.getName(), readableSize(folder), folder.toPath()));
            showFileTransferPage();
        }
    }

    private void addFiles(List<File> files) {
        for (File file : files) {
            pendingFiles.add(new TransferFile(file.getName(), readableSize(file), file.toPath()));
        }
        showFileTransferPage();
    }

    private String readableSize(File file) {
        if (file.isDirectory()) {
            return "文件夹";
        }
        long bytes = file.length();
        if (bytes >= 1024 * 1024) {
            return String.format("%.2f MB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private UserDevice sampleDeviceFor(int index) {
        List<UserDevice> devices = selectedTargets.isEmpty() ? recentTargets : selectedTargets;
        if (devices.isEmpty()) {
            return new UserDevice("empty", "未选择", "未选择目标", DeviceStatus.OFFLINE, "-", "?", "#5f656b", false);
        }
        return devices.get(Math.floorMod(index, devices.size()));
    }

    private void addTransferRow(GridPane table, int row, TransferTask task) {
        table.add(fileNameCell(task.fileName()), 0, row);
        table.add(mutedLabel(task.target().nickname() + " (" + task.target().deviceName() + ")", 14), 1, row);
        table.add(progressCell(task.progressPercent()), 2, row);
        table.add(mutedLabel(task.size(), 14), 3, row);
        table.add(mutedLabel(task.speed(), 14), 4, row);
        table.add(mutedLabel(task.elapsed(), 14), 5, row);
        table.add(statusBadge(task.status()), 6, row);
        table.add(operationCell(task.status()), 7, row);
    }

    private GridPane tableGrid(String... headers) {
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

    private HBox sectionHeader(String title, String count) {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().add(titleLabel(title, 18));
        if (count != null) {
            header.getChildren().add(tabPill(count, null, true));
        }
        return header;
    }

    private Label titleLabel(String text, int size) {
        Label label = new Label(text);
        label.getStyleClass().add("title-label");
        label.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, compactFontSize(size)));
        return label;
    }

    private Label mutedLabel(String text, int size) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-label");
        label.setFont(Font.font("Microsoft YaHei", compactFontSize(size)));
        return label;
    }

    private Label accentLabel(String text, int size) {
        Label label = new Label(text);
        label.getStyleClass().add("accent-label");
        label.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, compactFontSize(size)));
        return label;
    }

    private int compactFontSize(int size) {
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

    private TextField textField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("dark-input");
        return field;
    }

    private PasswordField passwordField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.getStyleClass().add("dark-input");
        return field;
    }

    private Node labeledField(String label, TextField field) {
        VBox box = new VBox(8, mutedLabel(label, 16), field);
        field.setPadding(new Insets(0, 14, 0, 14));
        return box;
    }

    private Button primaryButton(String text) {
        return textButton(text, "primary-button");
    }

    private Button secondaryButton(String text) {
        return textButton(text, "secondary-button");
    }

    private Button outlineButton(String text) {
        return textButton(text, "outline-button");
    }

    private Button ghostTextButton(String text) {
        return textButton(text, "ghost-button");
    }

    private Button compactButton(String text) {
        return textButton(text, "compact-button");
    }

    private Button textButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("text-button", styleClass);
        button.setCursor(Cursor.HAND);
        return button;
    }

    private Button windowButton(String iconLiteral, String tooltip) {
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

    private Label tabPill(String text, String count, boolean active) {
        Label pill = new Label(count == null ? text : text + "  " + count);
        pill.getStyleClass().addAll("ui-chip", active ? "ui-chip-active" : "ui-chip-muted", active ? "tab-pill-active" : "tab-pill");
        return pill;
    }

    private VBox glassSection(String title) {
        VBox box = new VBox(6);
        box.getStyleClass().add("glass-section");
        if (title != null && !title.isBlank()) {
            box.getChildren().add(titleLabel(title, 17));
            box.getChildren().add(separator());
        }
        return box;
    }

    private Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private Separator separator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("soft-separator");
        return separator;
    }

    private Separator separatorVertical() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().add("soft-separator");
        return separator;
    }

    private Region line() {
        Region line = new Region();
        line.getStyleClass().add("soft-line");
        HBox.setHgrow(line, Priority.ALWAYS);
        return line;
    }

    private Node createPlaneLogo(double size) {
        StackPane logo = new StackPane();
        logo.setMinSize(size, size);
        logo.setMaxSize(size, size);
        Polygon plane = new Polygon(size * 0.14, size * 0.42, size * 0.88, size * 0.10, size * 0.62, size * 0.88,
                size * 0.49, size * 0.57, size * 0.22, size * 0.55);
        plane.getStyleClass().add("plane-shape");
        logo.getChildren().add(plane);
        return logo;
    }

    private Node reviewIllustration() {
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

    private ProgressBar smallSpinner() {
        ProgressBar spinner = new ProgressBar();
        spinner.getStyleClass().add("small-spinner");
        spinner.setProgress(-1);
        return spinner;
    }

    private HBox statusLine(DeviceStatus status, String suffix) {
        return statusLine(status, suffix, 13);
    }

    private HBox statusLine(DeviceStatus status, String suffix, int size) {
        HBox line = new HBox(size <= 11 ? 4 : 8);
        line.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("●");
        dot.getStyleClass().add(status == DeviceStatus.ONLINE ? "online-dot" : "offline-dot");
        Label text = new Label(status == DeviceStatus.ONLINE ? "在线" : "离线");
        text.getStyleClass().add(status == DeviceStatus.ONLINE ? "online-text" : "muted-label");
        text.setFont(Font.font("Microsoft YaHei", compactFontSize(size)));
        line.getChildren().addAll(dot, text, mutedLabel(suffix, size));
        return line;
    }

    private Node avatar(String text, String color, double size) {
        StackPane avatar = new StackPane(new Label(text));
        avatar.getStyleClass().add("avatar");
        avatar.setStyle("-avatar-color: " + color + ";");
        avatar.setMinSize(size, size);
        avatar.setMaxSize(size, size);
        return avatar;
    }

    private Node connectionInfo() {
        VBox box = new VBox(8);
        box.getStyleClass().add("connection-info");
        box.getChildren().addAll(statusLine(DeviceStatus.ONLINE, "已连接"), mutedLabel("本机： DESKTOP-8F3K2M1", 13), mutedLabel("IP： 192.168.1.100", 13));
        return box;
    }

    private Node noticePill(String icon, String text) {
        HBox pill = new HBox(12, accentLabel(icon, 19), mutedLabel(text, 16));
        pill.getStyleClass().add("notice-pill");
        pill.setAlignment(Pos.CENTER);
        return pill;
    }

    private Node statCard(String label, String value, String color, String icon) {
        HBox card = new HBox(18);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(avatar(icon, color, 50), new VBox(8, mutedLabel(label, 15), titleLabel(value, 28)));
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Node statusCard(String title, String description, String color, boolean active) {
        HBox card = new HBox(12);
        card.getStyleClass().add(active ? "status-card-active" : "status-card");
        card.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("●");
        dot.setTextFill(Color.web(color));
        card.getChildren().addAll(dot, new VBox(6, titleLabel(title, 18), mutedLabel(description, 13)));
        return card;
    }

    private Node fileNameCell(String name) {
        return titleLabel(name, 14);
    }

    private Node progressCell(int percent) {
        HBox cell = new HBox(8);
        cell.setAlignment(Pos.CENTER_LEFT);
        Label value = mutedLabel(percent == 0 ? "—" : percent + "%", 14);
        ProgressBar progress = new ProgressBar(percent / 100.0);
        progress.getStyleClass().add("table-progress");
        progress.setPrefWidth(110);
        cell.getChildren().addAll(value, progress);
        return cell;
    }

    private Node operationCell(String status) {
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

    private Label statusBadge(String status) {
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

    private Node logLine(String line) {
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

    private TextField searchField(String prompt) {
        TextField field = textField(prompt);
        field.setMaxWidth(290);
        return field;
    }

    private CheckBox checkBox(String text, boolean selected) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setSelected(selected);
        checkBox.getStyleClass().add("dark-check");
        return checkBox;
    }

    private HBox ipColumn(String title, String value) {
        Button copyButton = compactButton("复制");
        copyButton.setOnAction(event -> copyToClipboard(value, "已复制 " + title));
        fixedWidth(copyButton, 48);
        HBox row = new HBox(10, new VBox(4, mutedLabel(title, 14), titleLabel(value, 17)), copyButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox limitRow(String title, int value) {
        TextField field = textField(String.valueOf(value));
        fixedWidth(field, 90);
        return new HBox(10, mutedLabel(title, 15), field, mutedLabel("MB/s", 14));
    }

    private StackPane colorSwatch(String color, boolean selected) {
        StackPane swatch = new StackPane();
        swatch.getStyleClass().add(selected ? "color-swatch-selected" : "color-swatch");
        swatch.setStyle("-swatch-color: " + color + ";");
        if (selected) {
            swatch.getChildren().add(new Label("✓"));
        }
        swatch.setCursor(Cursor.HAND);
        return swatch;
    }

    private ComboBox<String> comboBox(String value) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().add(value);
        combo.setValue(value);
        return combo;
    }

    private Circle radarCircle(double radius) {
        Circle circle = new Circle(radius);
        circle.getStyleClass().add("radar-circle");
        circle.setFill(Color.TRANSPARENT);
        return circle;
    }

    private Node scanDeviceLabel(UserDevice device, double x, double y) {
        StackPane wrapper = new StackPane(avatar(device.avatarText(), device.status() == DeviceStatus.ONLINE ? accentColor : "#5f656b", 52),
                mutedLabel(device.nickname(), 13));
        wrapper.setTranslateX(x);
        wrapper.setTranslateY(y);
        return wrapper;
    }

    private ColumnConstraints column(double width, boolean grow) {
        ColumnConstraints column = new ColumnConstraints();
        column.setMinWidth(0);
        column.setPrefWidth(width);
        if (grow) {
            column.setHgrow(Priority.ALWAYS);
        }
        return column;
    }

    private void addProfileRow(GridPane grid, int row, String label, String value, String counter, String action) {
        grid.add(mutedLabel(label, 14), 0, row);
        Label field = titleLabel(value, 16);
        StackPane cell = new StackPane(field);
        if (counter != null) {
            cell.getChildren().add(counterLabel(counter));
        }
        grid.add(cell, 1, row);
        Button button = secondaryButton(action);
        if ("复制".equals(action)) {
            button.setOnAction(event -> copyToClipboard(value, "已复制 " + label));
        }
        grid.add(button, 2, row);
    }

    private Label counterLabel(String counter) {
        Label label = mutedLabel(counter, 12);
        StackPane.setAlignment(label, Pos.CENTER_RIGHT);
        return label;
    }

    private void addInfoRow(GridPane grid, int row, String label, String value) {
        grid.add(mutedLabel(label, 14), 0, row);
        grid.add(titleLabel(value, 16), 1, row);
    }

    private Node fixedWidth(Region node, double width) {
        node.setMinWidth(width);
        node.setPrefWidth(width);
        node.setMaxWidth(width);
        return node;
    }

    private String displayName() {
        return profile == null ? "admin" : profile.nickname();
    }

    private String displayInitial() {
        return initialOf(displayName());
    }

    private String initialOf(String name) {
        if (name == null || name.isBlank()) {
            return "A";
        }
        return name.substring(0, 1).toUpperCase();
    }

    private void enableDrag(Node node) {
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

    private void copyToClipboard(String value, String message) {
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
        toast(message);
    }

    private void toast(String message) {
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
