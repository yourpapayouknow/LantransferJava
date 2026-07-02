package com.zjh.lanudp.ui.fx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FxFileTransferApp extends Application {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BackendFacade backend = new MockBackendFacade();
    private final List<TransferFile> pendingFiles = new ArrayList<>();
    private Profile profile;
    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.setTitle("极速互传");
        showStartup();
        stage.show();
    }

    private void showStartup() {
        backend.startApplication().thenAccept(state -> Platform.runLater(() -> {
            VBox steps = new VBox(10);
            for (StartupStep step : state.steps()) {
                steps.getChildren().add(row(step.title(), step.detail(), step.state().name()));
            }
            VBox page = centeredPage(
                    title("极速互传"),
                    muted("高速 · 安全 · 简单的文件传输工具"),
                    card("启动与初始化", new ProgressBar(state.progressPercent() / 100.0), steps),
                    primary("进入登录", event -> showAuth(false))
            );
            setScene(page, 860, 620);
        }));
    }

    private void showAuth(boolean registerMode) {
        TextField account = input("账号 / 邮箱 / 手机号");
        PasswordField password = password("密码");
        TextField device = input("当前设备名称");
        account.setText("admin");
        password.setText("admin");

        VBox form = new VBox(12);
        form.getStyleClass().add("form");
        form.getChildren().addAll(label("账号"), account, label("密码"), password);
        if (registerMode) {
            form.getChildren().addAll(label("设备名称"), device);
        }

        CheckBox remember = new CheckBox("记住我");
        Button submit = primary(registerMode ? "提交注册申请" : "登录", event -> {
            if (registerMode) {
                backend.register(new RegisterRequest(account.getText(), password.getText(), device.getText()))
                        .thenAccept(result -> Platform.runLater(this::showReviewPending));
                return;
            }
            backend.login(new LoginRequest(account.getText(), password.getText(), remember.isSelected()))
                    .thenAccept(result -> Platform.runLater(() -> {
                        if (!result.success()) {
                            toast(result.message());
                            return;
                        }
                        profile = result.profile();
                        showFileTransferPage();
                    }));
        });
        Button switchMode = secondary(registerMode ? "返回登录" : "注册账号", event -> showAuth(!registerMode));
        form.getChildren().addAll(remember, submit, switchMode);

        setScene(centeredPage(title("极速互传"), muted("极速传输，安全高效"), form), 760, 560);
    }

    private void showReviewPending() {
        showMain("注册审核提示", new VBox(16,
                title("注册申请已提交"),
                muted("您的账号正在等待管理员审核，通过后即可登录使用。"),
                primary("返回登录", event -> showAuth(false))
        ));
    }

    private void showFileTransferPage() {
        backend.loadRecentDevices().thenAccept(devices -> Platform.runLater(() -> {
            VBox fileList = new VBox(8);
            refreshFileList(fileList);

            Button pick = primary("选择文件", event -> {
                FileChooser chooser = new FileChooser();
                List<File> files = chooser.showOpenMultipleDialog(stage);
                if (files == null) {
                    return;
                }
                for (File file : files) {
                    pendingFiles.add(new TransferFile(file.getName(), readableSize(file.length()), file.toPath()));
                }
                refreshFileList(fileList);
            });
            Button send = secondary("开始传输", event -> startTransfer(devices));

            VBox page = new VBox(16,
                    card("待发送文件", new HBox(10, pick, send), fileList),
                    card("最近设备", deviceGrid(devices))
            );
            showMain("文件传输", page);
        }));
    }

    private void startTransfer(List<UserDevice> targets) {
        if (pendingFiles.isEmpty()) {
            toast("请先选择文件");
            return;
        }
        backend.startTransfer(pendingFiles, targets).thenAccept(summary -> Platform.runLater(() -> {
            VBox tasks = new VBox(8);
            for (TransferTask task : summary.tasks()) {
                tasks.getChildren().add(row(task.fileName(), task.target().nickname(), task.status()));
            }
            showMain("传输结果", new VBox(16,
                    card("结果概览", row("目标", summary.targetCount() + " 个", "成功 " + summary.successCount())),
                    card("任务列表", tasks)
            ));
        }));
    }

    private void showUserListPage() {
        backend.loadAllDevices().thenAccept(devices -> Platform.runLater(() ->
                showMain("用户列表", card("局域网用户", deviceGrid(devices)))));
    }

    private void showProfilePage() {
        if (profile == null) {
            showAuth(false);
            return;
        }
        TextField nickname = input("昵称");
        nickname.setText(profile.nickname());
        TextField signature = input("个性签名");
        signature.setText(profile.signature());
        showMain("我的", card("我的资料",
                row("用户 ID", profile.userId(), "版本 " + profile.version()),
                row("注册时间", profile.registeredAt().format(TIME_FORMAT), profile.language()),
                nickname,
                signature,
                primary("保存", event -> {
                    profile = new Profile(nickname.getText(), profile.userId(), profile.deviceName(), signature.getText(),
                            profile.registeredAt(), profile.lastLoginAt(), profile.version(), profile.language());
                    backend.updateProfile(profile);
                    toast("已保存");
                })
        ));
    }

    private void showSettingsPage() {
        TextField upload = input("上传速度限制");
        upload.setText("0");
        TextField retry = input("失败重试次数");
        retry.setText("3");
        showMain("系统设置", card("传输设置",
                row("本机局域网 IP", "等待真实后端提供", ""),
                label("上传速度限制"),
                upload,
                label("失败重试次数"),
                retry,
                primary("保存", event -> toast("设置已保存到占位后端"))
        ));
    }

    private void showMain(String title, Node page) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-shell");
        root.setTop(topbar(title));
        root.setLeft(nav());
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        root.setCenter(scroll);
        setScene(root, 1100, 720);
    }

    private HBox topbar(String title) {
        Label heading = title(title);
        Button logout = secondary("退出", event -> showAuth(false));
        HBox bar = new HBox(12, heading, spacer(), logout);
        bar.getStyleClass().add("topbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox nav() {
        VBox nav = new VBox(8,
                navButton("文件传输", this::showFileTransferPage),
                navButton("用户列表", this::showUserListPage),
                navButton("我的", this::showProfilePage),
                navButton("系统设置", this::showSettingsPage)
        );
        nav.getStyleClass().add("sidebar");
        return nav;
    }

    private Button navButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private GridPane deviceGrid(List<UserDevice> devices) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        for (int i = 0; i < devices.size(); i++) {
            UserDevice device = devices.get(i);
            VBox item = new VBox(6,
                    title(device.avatarText()),
                    label(device.nickname()),
                    muted(device.deviceName() + " · " + device.lastSeen())
            );
            item.getStyleClass().add(device.status() == DeviceStatus.ONLINE ? "device-online" : "device-offline");
            grid.add(item, i % 2, i / 2);
        }
        return grid;
    }

    private VBox card(String heading, Node... children) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.getChildren().add(title(heading));
        card.getChildren().addAll(children);
        return card;
    }

    private HBox row(String left, String center, String right) {
        HBox row = new HBox(12, label(left), muted(center), spacer(), muted(right));
        row.getStyleClass().add("row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox centeredPage(Node... children) {
        VBox page = new VBox(18, children);
        page.getStyleClass().add("center-page");
        page.setAlignment(Pos.CENTER);
        return page;
    }

    private void refreshFileList(VBox fileList) {
        fileList.getChildren().clear();
        if (pendingFiles.isEmpty()) {
            fileList.getChildren().add(muted("暂无文件"));
            return;
        }
        for (TransferFile file : pendingFiles) {
            fileList.getChildren().add(row(file.fileName(), file.size(), file.path().toString()));
        }
    }

    private void setScene(Parent root, int width, int height) {
        Scene scene = new Scene(root, width, height);
        String css = getClass().getResource("app.css").toExternalForm();
        scene.getStylesheets().add(css);
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(520);
    }

    private Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("title");
        return label;
    }

    private Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("label");
        return label;
    }

    private Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        return label;
    }

    private TextField input(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        return field;
    }

    private PasswordField password(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        return field;
    }

    private Button primary(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        button.setOnAction(action);
        return button;
    }

    private Button secondary(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(action);
        return button;
    }

    private Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private void toast(String message) {
        Label label = muted(message);
        StackPane popup = new StackPane(label);
        popup.getStyleClass().add("toast");
        Stage toast = new Stage();
        toast.initOwner(stage);
        toast.setScene(new Scene(popup, 280, 80));
        toast.show();
    }
}
