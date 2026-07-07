package com.iwmei.lantransfer.view;

import com.iwmei.lantransfer.model.LoginRequest;
import com.iwmei.lantransfer.model.RegisterRequest;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

// 登录注册页面逻辑
final class Auth {
    private final MainWindow app;

    // 初始化登录注册页面对象
    Auth(MainWindow app) {
        this.app = app;
    }

    // 显示登录或注册页面
    void show(boolean registerMode) {
        VBox body = new VBox(24);
        body.getStyleClass().add("auth-body");
        body.setAlignment(Pos.CENTER);

        HBox brand = new HBox(18, app.createPlaneLogo(58), new VBox(4,
                app.titleLabel("极速互传", 30),
                app.mutedLabel("极速传输，安全高效", 16)
        ));
        brand.setAlignment(Pos.CENTER);

        Node form = registerMode ? registerForm() : loginForm();
        body.getChildren().addAll(brand, form);
        app.setAuthPage(body);
    }

    // 显示注册审核等待页面
    void showReviewPending() {
        VBox page = new VBox(20);
        page.getStyleClass().add("page-content");
        HBox breadcrumb = new HBox(16, app.secondaryButton("返回"), new Separator(Orientation.VERTICAL), app.accentLabel("注册审核提示", 16));
        breadcrumb.setAlignment(Pos.CENTER_LEFT);
        ((Button) breadcrumb.getChildren().get(0)).setOnAction(event -> app.showAuth(false));

        VBox card = new VBox(22);
        card.getStyleClass().add("pending-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(82, 44, 70, 44));
        Button ok = app.primaryButton("我知道了");
        Button back = app.secondaryButton("返回登录");
        ok.setOnAction(event -> app.showAuth(false));
        back.setOnAction(event -> app.showAuth(false));
        card.getChildren().addAll(
                app.reviewIllustration(),
                app.titleLabel("注册申请已提交", 30),
                app.mutedLabel("您的账号正在等待管理员审核，通过后即可登录使用", 18),
                app.noticePill("◷", "审核通常会在 1 个工作日 内完成，请耐心等待。"),
                app.fixedWidth(ok, 328),
                app.fixedWidth(back, 328)
        );
        page.getChildren().addAll(breadcrumb, card);
        app.setMainPage("文件传输", page, false, false);
    }

    // 构建登录表单区域
    private Node loginForm() {
        VBox form = new VBox(14);
        form.getStyleClass().add("auth-form");
        TextField account = app.textField("请输入账号");
        PasswordField password = app.passwordField("请输入密码");
        account.setText("admin");
        password.setText("admin");
        CheckBox rememberMe = app.checkBox("记住我", false);

        Button loginButton = app.primaryButton("登录");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(event -> app.controller.login(new LoginRequest(account.getText().trim(), password.getText(), rememberMe.isSelected()))
                .thenAccept(result -> Platform.runLater(() -> {
                    if (result.success()) {
                        app.profile = result.profile();
                        app.showFileTransferPage();
                    } else {
                        app.toast(result.message());
                    }
                })));

        Button registerButton = app.outlineButton("注册账号");
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setOnAction(event -> app.showAuth(true));
        HBox divider = new HBox(18, app.line(), app.mutedLabel("或", 14), app.line());
        divider.setAlignment(Pos.CENTER);
        form.getChildren().addAll(app.labeledField("账号", account), app.labeledField("密码", password), rememberMe, loginButton, divider, registerButton);
        return form;
    }

    // 构建注册表单区域
    private Node registerForm() {
        VBox form = new VBox(14);
        form.getStyleClass().add("auth-form");
        TextField account = app.textField("请输入账号");
        PasswordField password = app.passwordField("请输入密码");
        TextField device = app.textField("当前设备名称");
        Button submit = app.primaryButton("提交注册申请");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setOnAction(event -> app.controller.register(new RegisterRequest(account.getText().trim(), password.getText(), device.getText().trim()))
                .thenAccept(result -> Platform.runLater(() -> {
                    app.profile = result.profile();
                    showReviewPending();
                })));
        Button backLogin = app.outlineButton("已有账号，返回登录");
        backLogin.setMaxWidth(Double.MAX_VALUE);
        backLogin.setOnAction(event -> app.showAuth(false));
        form.getChildren().addAll(app.labeledField("账号", account), app.labeledField("密码", password), app.labeledField("设备名称", device), submit, backLogin);
        return form;
    }
}
