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
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
final class Auth {
    private final MainWindow app;
    Auth(MainWindow app) {
        this.app = app;
    }
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
    private Node loginForm() {
        VBox form = new VBox(14);
        form.getStyleClass().add("auth-form");
        TextField account = app.textField("请输入账号");
        PasswordBox password = passwordBox("请输入密码");
        CheckBox rememberMe = app.checkBox("记住我", false);
        app.controller.loadRememberedAccount().thenAccept(saved -> Platform.runLater(() -> {
            if (saved != null && !saved.isBlank()) {
                account.setText(saved);
                password.clear();
                rememberMe.setSelected(true);
            }
        }));
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
        form.getChildren().addAll(app.labeledField("账号", account), labeledPassword("密码", password), rememberMe, loginButton, divider, registerButton);
        return form;
    }
    private Node registerForm() {
        VBox form = new VBox(14);
        form.getStyleClass().add("auth-form");
        TextField account = app.textField("请输入账号");
        PasswordBox password = passwordBox("请输入密码");
        TextField device = app.textField("当前设备名称");
        Button submit = app.primaryButton("注册");
        submit.setMaxWidth(Double.MAX_VALUE);
        ProgressIndicator loading = new ProgressIndicator();
        loading.setMaxSize(24, 24);
        loading.setVisible(false);
        StackPane submitBox = new StackPane(submit, loading);
        submitBox.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(loading, Pos.CENTER_RIGHT);
        StackPane.setMargin(loading, new Insets(0, 12, 0, 0));
        submit.setOnAction(event -> {
            loading.setVisible(true);
            submit.setDisable(true);
            account.setDisable(true);
            password.setDisable(true);
            device.setDisable(true);
            app.controller.register(new RegisterRequest(account.getText().trim(), password.getText(), device.getText().trim()))
                .thenAccept(result -> Platform.runLater(() -> {
                    loading.setVisible(false);
                    submit.setDisable(false);
                    account.setDisable(false);
                    password.setDisable(false);
                    device.setDisable(false);
                    if (!result.success()) {
                        app.toast(result.message());
                    } else if (result.pendingReview()) {
                        app.profile = result.profile();
                        showReviewPending();
                    } else {
                        app.toast(result.message());
                        app.showAuth(false);
                    }
                }));
        });
        Button backLogin = app.outlineButton("已有账号，返回登录");
        backLogin.setMaxWidth(Double.MAX_VALUE);
        backLogin.setOnAction(event -> app.showAuth(false));
        form.getChildren().addAll(app.labeledField("账号", account), labeledPassword("密码", password), app.labeledField("设备名称", device), submitBox, backLogin);
        return form;
    }
    private PasswordBox passwordBox(String prompt) {
        PasswordField hidden = app.passwordField(prompt);
        TextField shown = app.textField(prompt);
        hidden.setPadding(new Insets(0, 14, 0, 14));
        shown.setPadding(new Insets(0, 14, 0, 14));
        shown.textProperty().bindBidirectional(hidden.textProperty());
        shown.setVisible(false);
        shown.setManaged(false);
        StackPane fields = new StackPane(hidden, shown);
        fields.setMaxWidth(Double.MAX_VALUE);
        hidden.setMaxWidth(Double.MAX_VALUE);
        shown.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(fields, Priority.ALWAYS);
        Button eye = app.iconToggleButton("mdi2e-eye", "显示密码", false);
        eye.setOnAction(event -> {
            boolean show = !shown.isVisible();
            shown.setVisible(show);
            shown.setManaged(show);
            hidden.setVisible(!show);
            hidden.setManaged(!show);
            ((FontIcon) eye.getGraphic()).setIconLiteral(show ? "mdi2e-eye-off" : "mdi2e-eye");
            eye.setTooltip(new Tooltip(show ? "隐藏密码" : "显示密码"));
        });
        return new PasswordBox(new HBox(8, fields, eye), hidden, shown);
    }
    private Node labeledPassword(String label, PasswordBox box) {
        return new VBox(8, app.mutedLabel(label, 16), box.node());
    }
    private record PasswordBox(Node node, PasswordField hidden, TextField shown) {
        private String getText() {
            return hidden.getText();
        }

        // 清空密码文本
        private void clear() {
            hidden.clear();
        }
        private void setDisable(boolean value) {
            hidden.setDisable(value);
            shown.setDisable(value);
            node.setDisable(value);
        }
    }
}
