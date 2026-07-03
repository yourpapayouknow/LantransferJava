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
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    String accentColor = ACCENT_ORANGE;
    boolean userListGridView;
    boolean recentTargetsLoaded;
    int userListPage;
    double requestedWindowWidth = -1;
    double requestedWindowHeight = -1;
    double dragOffsetX;
    double dragOffsetY;

    final Auth auth = new Auth(this);
    final FileTransfer fileTransfer = new FileTransfer(this);
    final UserList userList = new UserList(this);
    final Scan scan = new Scan(this);
    final Mine mine = new Mine(this);
    final Settings settings = new Settings(this);


    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle(APP_TITLE);
        showAuth(false);
    }

    void showAuth(boolean registerMode) {
        auth.show(registerMode);
    }

    void showReviewPending() {
        auth.showReviewPending();
    }

    void showFileTransferPage() {
        fileTransfer.showFileTransferPage();
    }

    void showTransferResultPage() {
        fileTransfer.showTransferResultPage();
    }

    void showUserListPage() {
        userList.showUserListPage();
    }

    void showScanPage() {
        scan.showScanPage();
    }

    void showProfilePage() {
        mine.showProfilePage();
    }

    void showSettingsPage() {
        settings.showSettingsPage();
    }

    void setAuthPage(Node body) {
        setWindow(windowShell(body), AUTH_WIDTH, AUTH_HEIGHT, AUTH_WIDTH, AUTH_HEIGHT);
    }

    void setMainPage(String activeNav, Node page, boolean topActions, boolean footer) {
        setWindow(mainWindowShell(activeNav, page, topActions, footer), MAIN_WIDTH, MAIN_HEIGHT, MAIN_MIN_WIDTH, MAIN_MIN_HEIGHT);
    }


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

    void addCard(GridPane grid, Node card, int index, int columns) {
        GridPane.setHgrow(card, Priority.ALWAYS);
        GridPane.setFillWidth(card, true);
        grid.add(card, index % columns, index / columns);
    }

    Parent windowShell(Node body) {
        VBox shell = new VBox();
        shell.getStyleClass().add("window-shell");
        shell.setMinSize(0, 0);
        shell.getChildren().addAll(titleBar(), body);
        VBox.setVgrow(body, Priority.ALWAYS);
        return appRoot(shell);
    }

    Parent mainWindowShell(String activeNav, Node page, boolean topActions, boolean footer) {
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

    Parent appRoot(Node shell) {
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

    void clipToRoundedWindow(Region region) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        region.setClip(clip);
    }

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
    }

    HBox titleBar() {
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

    VBox sidebar(String activeNav, boolean includeScan) {
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

    HBox mainTopbar() {
        HBox topbar = new HBox(14);
        topbar.getStyleClass().add("content-topbar");
        topbar.setAlignment(Pos.CENTER_RIGHT);
        Button theme = ghostTextButton("主题");
        theme.setOnAction(event -> showSettingsPage());
        topbar.getChildren().addAll(spacer(), theme, new Separator(Orientation.VERTICAL), avatar(displayInitial(), "#c8d1dc", 34), new Label(displayName()));
        return topbar;
    }

    HBox statusFooter() {
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

    StackPane radar(List<UserDevice> devices) {
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

    Node userCard(UserDevice device, boolean large) {
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

    void addRecentTarget(UserDevice device) {
        recentTargetsLoaded = true;
        recentTargets.remove(device);
        if (recentTargets.size() >= 5) {
            recentTargets.remove(0);
        }
        recentTargets.add(device);
        selectedTargets.remove(device);
        selectedTargets.add(device);
    }

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

    HBox sectionHeader(String title, String count) {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().add(titleLabel(title, 18));
        if (count != null) {
            header.getChildren().add(tabPill(count, null, true));
        }
        return header;
    }

    Label titleLabel(String text, int size) {
        Label label = new Label(text);
        label.getStyleClass().add("title-label");
        label.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, compactFontSize(size)));
        return label;
    }

    Label mutedLabel(String text, int size) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-label");
        label.setFont(Font.font("Microsoft YaHei", compactFontSize(size)));
        return label;
    }

    Label accentLabel(String text, int size) {
        Label label = new Label(text);
        label.getStyleClass().add("accent-label");
        label.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, compactFontSize(size)));
        return label;
    }

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

    TextField textField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("dark-input");
        return field;
    }

    PasswordField passwordField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.getStyleClass().add("dark-input");
        return field;
    }

    Node labeledField(String label, TextField field) {
        VBox box = new VBox(8, mutedLabel(label, 16), field);
        field.setPadding(new Insets(0, 14, 0, 14));
        return box;
    }

    Button primaryButton(String text) {
        return textButton(text, "primary-button");
    }

    Button secondaryButton(String text) {
        return textButton(text, "secondary-button");
    }

    Button outlineButton(String text) {
        return textButton(text, "outline-button");
    }

    Button ghostTextButton(String text) {
        return textButton(text, "ghost-button");
    }

    Button compactButton(String text) {
        return textButton(text, "compact-button");
    }

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

    Button textButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("text-button", styleClass);
        button.setCursor(Cursor.HAND);
        return button;
    }

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

    Label tabPill(String text, String count, boolean active) {
        Label pill = new Label(count == null ? text : text + "  " + count);
        pill.getStyleClass().addAll("ui-chip", active ? "ui-chip-active" : "ui-chip-muted", active ? "tab-pill-active" : "tab-pill");
        return pill;
    }

    VBox glassSection(String title) {
        VBox box = new VBox(6);
        box.getStyleClass().add("glass-section");
        if (title != null && !title.isBlank()) {
            box.getChildren().add(titleLabel(title, 17));
            box.getChildren().add(separator());
        }
        return box;
    }

    Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    Separator separator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("soft-separator");
        return separator;
    }

    Separator separatorVertical() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().add("soft-separator");
        return separator;
    }

    Region line() {
        Region line = new Region();
        line.getStyleClass().add("soft-line");
        HBox.setHgrow(line, Priority.ALWAYS);
        return line;
    }

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

    ProgressBar smallSpinner() {
        ProgressBar spinner = new ProgressBar();
        spinner.getStyleClass().add("small-spinner");
        spinner.setProgress(-1);
        return spinner;
    }

    HBox statusLine(DeviceStatus status, String suffix) {
        return statusLine(status, suffix, 13);
    }

    HBox statusLine(DeviceStatus status, String suffix, int size) {
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

    Node avatar(String text, String color, double size) {
        StackPane avatar = new StackPane(new Label(text));
        avatar.getStyleClass().add("avatar");
        avatar.setStyle("-avatar-color: " + color + ";");
        avatar.setMinSize(size, size);
        avatar.setMaxSize(size, size);
        return avatar;
    }

    Node connectionInfo() {
        VBox box = new VBox(8);
        box.getStyleClass().add("connection-info");
        box.getChildren().addAll(statusLine(DeviceStatus.ONLINE, "已连接"), mutedLabel("本机： DESKTOP-8F3K2M1", 13), mutedLabel("IP： 192.168.1.100", 13));
        return box;
    }

    Node noticePill(String icon, String text) {
        HBox pill = new HBox(12, accentLabel(icon, 19), mutedLabel(text, 16));
        pill.getStyleClass().add("notice-pill");
        pill.setAlignment(Pos.CENTER);
        return pill;
    }

    Node statCard(String label, String value, String color, String icon) {
        HBox card = new HBox(18);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(avatar(icon, color, 50), new VBox(8, mutedLabel(label, 15), titleLabel(value, 28)));
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    Node statusCard(String title, String description, String color, boolean active) {
        HBox card = new HBox(12);
        card.getStyleClass().add(active ? "status-card-active" : "status-card");
        card.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("●");
        dot.setTextFill(Color.web(color));
        card.getChildren().addAll(dot, new VBox(6, titleLabel(title, 18), mutedLabel(description, 13)));
        return card;
    }

    Node fileNameCell(String name) {
        return titleLabel(name, 14);
    }

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

    TextField searchField(String prompt) {
        TextField field = textField(prompt);
        field.setMaxWidth(290);
        return field;
    }

    CheckBox checkBox(String text, boolean selected) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setSelected(selected);
        checkBox.getStyleClass().add("dark-check");
        return checkBox;
    }

    HBox ipColumn(String title, String value) {
        Button copyButton = compactButton("复制");
        copyButton.setOnAction(event -> copyToClipboard(value, "已复制 " + title));
        fixedWidth(copyButton, 48);
        HBox row = new HBox(10, new VBox(4, mutedLabel(title, 14), titleLabel(value, 17)), copyButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    HBox limitRow(String title, int value) {
        TextField field = textField(String.valueOf(value));
        fixedWidth(field, 90);
        return new HBox(10, mutedLabel(title, 15), field, mutedLabel("MB/s", 14));
    }

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

    ComboBox<String> comboBox(String value) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().add(value);
        combo.setValue(value);
        return combo;
    }

    Circle radarCircle(double radius) {
        Circle circle = new Circle(radius);
        circle.getStyleClass().add("radar-circle");
        circle.setFill(Color.TRANSPARENT);
        return circle;
    }

    Node scanDeviceLabel(UserDevice device, double x, double y) {
        StackPane wrapper = new StackPane(avatar(device.avatarText(), device.status() == DeviceStatus.ONLINE ? accentColor : "#5f656b", 52),
                mutedLabel(device.nickname(), 13));
        wrapper.setTranslateX(x);
        wrapper.setTranslateY(y);
        return wrapper;
    }

    ColumnConstraints column(double width, boolean grow) {
        ColumnConstraints column = new ColumnConstraints();
        column.setMinWidth(0);
        column.setPrefWidth(width);
        if (grow) {
            column.setHgrow(Priority.ALWAYS);
        }
        return column;
    }

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
            button.setOnAction(event -> copyToClipboard(value, "已复制 " + label));
        }
        grid.add(button, 2, row);
    }

    Label counterLabel(String counter) {
        Label label = mutedLabel(counter, 12);
        StackPane.setAlignment(label, Pos.CENTER_RIGHT);
        return label;
    }

    void addInfoRow(GridPane grid, int row, String label, String value) {
        grid.add(mutedLabel(label, 14), 0, row);
        grid.add(titleLabel(value, 16), 1, row);
    }

    Node fixedWidth(Region node, double width) {
        node.setMinWidth(width);
        node.setPrefWidth(width);
        node.setMaxWidth(width);
        return node;
    }

    String displayName() {
        return profile == null ? "admin" : profile.nickname();
    }

    String displayInitial() {
        return initialOf(displayName());
    }

    String initialOf(String name) {
        if (name == null || name.isBlank()) {
            return "A";
        }
        return name.substring(0, 1).toUpperCase();
    }

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

    void copyToClipboard(String value, String message) {
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
        toast(message);
    }

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
