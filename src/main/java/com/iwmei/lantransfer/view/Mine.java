package com.iwmei.lantransfer.view;

import com.iwmei.lantransfer.model.Profile;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

final class Mine {
    private final MainWindow app;

    Mine(MainWindow app) {
        this.app = app;
    }

    void showProfilePage() {
        if (app.profile == null) {
            app.showAuth(false);
            return;
        }
        VBox page = new VBox(14);
        page.getStyleClass().add("page-content");
        VBox profileSection = app.glassSection("我的资料");
        profileSection.getChildren().add(profileEditor(app.profile));
        VBox statusSection = app.glassSection("状态设置");
        statusSection.getChildren().add(statusCards());
        statusSection.getChildren().add(customStatusField());
        VBox moreSection = app.glassSection("更多信息");
        moreSection.getChildren().add(moreInfo(app.profile));
        HBox actions = new HBox(20, app.fixedWidth(app.primaryButton("保存"), 164), app.fixedWidth(app.secondaryButton("重置"), 164));
        actions.setAlignment(Pos.CENTER);
        page.getChildren().addAll(profileSection, statusSection, moreSection, actions);
        app.setMainPage("我的", page, true, true);
    }

    private HBox profileEditor(Profile profile) {
        HBox root = new HBox(28);
        root.setAlignment(Pos.CENTER_LEFT);
        StackPane photo = new StackPane(app.avatar(app.initialOf(profile.nickname()), "#d6dee8", 120));
        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(10);
        fields.getColumnConstraints().addAll(app.column(96, false), app.column(470, true), app.column(88, false));
        app.addProfileRow(fields, 0, "昵称", profile.nickname(), "2/20", "编辑");
        app.addProfileRow(fields, 1, "用户ID", profile.userId(), null, "复制");
        app.addProfileRow(fields, 2, "设备名称", profile.deviceName(), null, "编辑");
        app.addProfileRow(fields, 3, "个性签名", profile.signature(), "9/60", "编辑");
        root.getChildren().addAll(photo, fields);
        return root;
    }

    private GridPane statusCards() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getColumnConstraints().addAll(app.column(160, true), app.column(180, true), app.column(180, true), app.column(180, true), app.column(180, true));
        grid.add(app.statusCard("默认状态", "自动检测在线状态", "#6f7782", true), 0, 0);
        grid.add(app.statusCard("在线", "允许被发现并接收文件", "#2ecc40", false), 1, 0);
        grid.add(app.statusCard("忙碌", "接收前先提示确认", "#ffb22c", false), 2, 0);
        grid.add(app.statusCard("隐身", "不出现在扫描列表", "#8a52d8", false), 3, 0);
        grid.add(app.statusCard("离线", "暂停所有传输", "#9aa0a6", false), 4, 0);
        return grid;
    }

    private HBox customStatusField() {
        TextField field = app.textField("输入自定义状态");
        Button save = app.outlineButton("保存");
        save.setOnAction(event -> app.toast("状态设置接口已预留。"));
        HBox row = new HBox(12, field, save);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

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
