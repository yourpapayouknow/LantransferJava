package com.iwmei.lantransfer.view;

import com.iwmei.lantransfer.model.Profile;
import com.iwmei.lantransfer.model.UserStatus;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;

// 我的资料页面逻辑
final class Mine {
    private static final double PROFILE_FORM_WIDTH = 542;
    private static final double PHOTO_SIZE = 136;
    private final MainWindow app;
    private TextField nicknameField;
    private TextField deviceNameField;
    private TextField signatureField;
    private UserStatus selectedStatus = UserStatus.DEFAULT;

    // 初始化我的页面对象
    Mine(MainWindow app) {
        this.app = app;
    }

    // 显示我的资料页面
    void showProfilePage() {
        if (app.profile == null) {
            app.showAuth(false);
            return;
        }
        selectedStatus = app.profile.status() == null ? UserStatus.DEFAULT : app.profile.status();
        VBox page = new VBox(14);
        page.getStyleClass().add("page-content");
        VBox profileSection = app.glassSection("我的资料");
        profileSection.getChildren().add(profileEditor(app.profile));
        VBox statusSection = app.glassSection("状态设置");
        statusSection.getChildren().add(statusCards());
        statusSection.getChildren().add(customStatusField());
        VBox moreSection = app.glassSection("更多信息");
        moreSection.getChildren().add(moreInfo(app.profile));
        Button save = app.primaryButton("保存");
        save.setOnAction(event -> {
            app.profile = readProfile();
            app.controller.updateProfile(app.profile);
            app.toast("资料已保存");
            showProfilePage();
        });
        Button reset = app.secondaryButton("重置");
        reset.setOnAction(event -> showProfilePage());
        HBox actions = new HBox(20, app.fixedWidth(save, 164), app.fixedWidth(reset, 164));
        actions.setAlignment(Pos.CENTER);
        page.getChildren().addAll(profileSection, statusSection, moreSection, actions);
        app.setMainPage("我的", page, true, true);
    }

    // 构建个人资料编辑区域
    private HBox profileEditor(Profile profile) {
        HBox root = new HBox(28);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setMaxWidth(Double.MAX_VALUE);
        root.setPadding(new Insets(16));
        StackPane photo = profilePhoto(profile);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(10);
        fields.getColumnConstraints().addAll(app.column(96, false), app.column(382, true), app.column(40, false));
        app.fixedWidth(fields, PROFILE_FORM_WIDTH);
        nicknameField = editableRow(fields, 0, "昵称", profile.nickname());
        profileRow(fields, 1, "用户ID", profile.userId());
        deviceNameField = editableRow(fields, 2, "设备名称", profile.deviceName());
        signatureField = editableRow(fields, 3, "个性签名", profile.signature());
        root.getChildren().addAll(photo, spacer, fields);
        return root;
    }

    // 构建可修改头像节点
    private StackPane profilePhoto(Profile profile) {
        Label mask = new Label("修改");
        mask.getStyleClass().add("profile-avatar-mask");
        mask.setMinSize(PHOTO_SIZE, PHOTO_SIZE);
        mask.setMaxSize(PHOTO_SIZE, PHOTO_SIZE);
        StackPane photo = new StackPane(app.avatar(app.initialOf(profile.nickname()), "#d6dee8", PHOTO_SIZE, profile.avatar()), mask);
        photo.getStyleClass().add("profile-avatar-wrap");
        photo.setOnMouseClicked(event -> chooseAvatar());
        return photo;
    }

    // 选择头像文件并保存到当前资料
    private void chooseAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择头像");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("图片", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif"));
        File file = chooser.showOpenDialog(app.stage);
        if (file == null) {
            return;
        }
        try {
            app.profile = withAvatar(readProfile(), jpegAvatar(file));
            app.controller.updateProfile(app.profile);
            app.toast("头像已保存");
            showProfilePage();
        } catch (Exception ex) {
            app.toast("头像处理失败");
        }
    }

    // 把头像压缩成 128px JPEG 的 Base64 文本
    private String jpegAvatar(File file) throws Exception {
        BufferedImage source = ImageIO.read(file);
        if (source == null) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        BufferedImage out = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, 128, 128);
        graphics.drawImage(source, 0, 0, 128, 128, x, y, x + side, y + side, null);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.72f);
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(out, null, null), param);
        } finally {
            writer.dispose();
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    // 向资料表单加入可编辑字段行
    private TextField editableRow(GridPane grid, int row, String label, String value) {
        addProfileLabel(grid, row, label);
        TextField field = app.textField(label);
        field.setText(value);
        field.setEditable(false);
        field.setMaxWidth(Double.MAX_VALUE);
        grid.add(field, 1, row);
        Button edit = profileIconButton("mdi2p-pencil", "编辑", "compact-button");
        edit.setOnAction(event -> {
            boolean editing = !field.isEditable();
            field.setEditable(editing);
            profileIcon(edit, editing ? "mdi2c-check" : "mdi2p-pencil", editing ? "保存" : "编辑");
            edit.getStyleClass().removeAll("compact-button", "primary-button");
            edit.getStyleClass().add(editing ? "primary-button" : "compact-button");
            if (editing) {
                field.requestFocus();
                field.positionCaret(field.getText().length());
                return;
            }
            app.profile = readProfile();
            app.controller.updateProfile(app.profile);
            app.toast(label + "已保存");
        });
        grid.add(edit, 2, row);
        return field;
    }

    // 向资料表单加入只读字段行
    private void profileRow(GridPane grid, int row, String label, String value) {
        addProfileLabel(grid, row, label);
        Label field = app.titleLabel(value, 16);
        StackPane cell = new StackPane(field);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setMaxWidth(Double.MAX_VALUE);
        grid.add(cell, 1, row);
        Button copy = profileIconButton("mdi2c-content-copy", "复制", "compact-button");
        copy.setOnAction(event -> app.copyToClipboard(value, "已复制" + label));
        grid.add(copy, 2, row);
    }

    // 向资料表单加入右对齐标签
    private void addProfileLabel(GridPane grid, int row, String text) {
        Label label = app.mutedLabel(text, 14);
        GridPane.setHalignment(label, HPos.RIGHT);
        grid.add(label, 0, row);
    }

    // 构建资料表单图标按钮
    private Button profileIconButton(String iconCode, String tooltip, String styleClass) {
        Button button = app.compactButton("");
        app.fixedWidth(button, 36);
        profileIcon(button, iconCode, tooltip);
        button.getStyleClass().remove("compact-button");
        button.getStyleClass().add(styleClass);
        return button;
    }

    // 设置资料表单图标按钮内容
    private void profileIcon(Button button, String iconCode, String tooltip) {
        FontIcon icon = new FontIcon(iconCode);
        icon.getStyleClass().add("button-font-icon");
        icon.setIconSize(15);
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltip));
    }

    // 构建状态选择卡片区域
    private GridPane statusCards() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getColumnConstraints().addAll(app.column(160, true), app.column(180, true), app.column(180, true), app.column(180, true), app.column(180, true));
        grid.add(statusCard(UserStatus.DEFAULT, "默认状态", "自动检测在线状态", "#6f7782"), 0, 0);
        grid.add(statusCard(UserStatus.ONLINE, "在线", "允许被发现并接收文件", "#2ecc40"), 1, 0);
        grid.add(statusCard(UserStatus.BUSY, "忙碌", "接收前先提示确认", "#ffb22c"), 2, 0);
        grid.add(statusCard(UserStatus.INVISIBLE, "隐身", "不出现在扫描列表", "#8a52d8"), 3, 0);
        grid.add(statusCard(UserStatus.OFFLINE, "离线", "暂停所有传输", "#9aa0a6"), 4, 0);
        return grid;
    }

    // 构建可点击状态卡
    private Node statusCard(UserStatus status, String title, String description, String color) {
        Node card = app.statusCard(title, description, color, selectedStatus == status);
        card.setOnMouseClicked(event -> saveStatus(status, statusText(status)));
        return card;
    }

    // 构建自定义状态输入区域
    private HBox customStatusField() {
        TextField field = app.textField("输入自定义状态");
        field.setText(app.profile.signature());
        Button save = app.outlineButton("保存");
        save.setOnAction(event -> {
            String text = field.getText().trim();
            saveStatus(selectedStatus, text.isBlank() ? statusText(selectedStatus) : text);
        });
        HBox row = new HBox(12, field, save);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    // 保存当前状态和状态文本
    private void saveStatus(UserStatus status, String text) {
        selectedStatus = status == null ? UserStatus.DEFAULT : status;
        app.controller.updateStatus(selectedStatus, text);
        app.profile = withStatus(withSignature(app.profile, text), selectedStatus);
        app.toast("状态已保存");
        showProfilePage();
    }

    // 复制资料并替换个性签名
    private Profile withSignature(Profile profile, String signature) {
        return new Profile(profile.nickname(), profile.userId(), profile.deviceName(), signature,
                profile.registeredAt(), profile.lastLoginAt(), profile.version(), profile.language(), profile.status(), profile.avatar());
    }

    // 复制资料并替换用户状态
    private Profile withStatus(Profile profile, UserStatus status) {
        return new Profile(profile.nickname(), profile.userId(), profile.deviceName(), profile.signature(),
                profile.registeredAt(), profile.lastLoginAt(), profile.version(), profile.language(), status, profile.avatar());
    }

    // 复制资料并替换头像
    private Profile withAvatar(Profile profile, String avatar) {
        return new Profile(profile.nickname(), profile.userId(), profile.deviceName(), profile.signature(),
                profile.registeredAt(), profile.lastLoginAt(), profile.version(), profile.language(), profile.status(), avatar);
    }

    // 从资料表单读取当前资料
    private Profile readProfile() {
        return new Profile(text(nicknameField, app.profile.nickname()), app.profile.userId(),
                text(deviceNameField, app.profile.deviceName()), text(signatureField, app.profile.signature()),
                app.profile.registeredAt(), app.profile.lastLoginAt(), app.profile.version(), app.profile.language(), selectedStatus,
                app.profile.avatar());
    }

    // 读取文本输入值
    private String text(TextField field, String fallback) {
        String value = field == null ? "" : field.getText().trim();
        return value.isBlank() ? fallback : value;
    }

    // 返回用户状态默认文案
    private String statusText(UserStatus status) {
        return switch (status == null ? UserStatus.DEFAULT : status) {
            case ONLINE -> "在线，允许接收文件";
            case BUSY -> "忙碌，接收前请确认";
            case INVISIBLE -> "隐身，不参与扫描";
            case OFFLINE -> "离线，暂停传输";
            case DEFAULT -> "在线，已连接";
        };
    }

    // 构建账号更多信息区域
    private GridPane moreInfo(Profile profile) {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(10);
        grid.getColumnConstraints().addAll(app.column(160, false), app.column(Region.USE_COMPUTED_SIZE, true), app.column(180, false));
        app.addInfoRow(grid, 0, "注册时间", MainWindow.DATE_TIME.format(profile.registeredAt()));
        app.addInfoRow(grid, 1, "最后登录", MainWindow.DATE_TIME.format(profile.lastLoginAt()));
        app.addInfoRow(grid, 2, "版本信息", profile.version());
        return grid;
    }
}
