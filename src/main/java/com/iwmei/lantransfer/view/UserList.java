package com.iwmei.lantransfer.view;

import com.iwmei.lantransfer.model.Group;
import com.iwmei.lantransfer.model.UserDevice;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 用户列表页面逻辑
final class UserList {
    private final MainWindow app;
    private final List<UserDevice> groupDraft = new ArrayList<>();
    private final Set<String> expandedGroups = new HashSet<>();
    private String query = "";
    private String groupName = "";
    private String groupCode = "";
    private String editingGroup = "";
    private boolean grouping;

    // 初始化用户列表页面对象
    UserList(MainWindow app) {
        this.app = app;
    }

    // 显示用户列表页面
    void showUserListPage() {
        app.controller.loadAllDevices()
                .thenCombine(app.controller.loadGroups(), AbstractMap.SimpleEntry::new)
                .thenAccept(data -> Platform.runLater(() -> renderPage(data.getKey(), data.getValue())))
                .exceptionally(error -> {
                    Platform.runLater(() -> app.toast("加载用户列表失败"));
                    return null;
                });
    }

    // 渲染用户列表页面主体
    private void renderPage(List<UserDevice> devices, List<Group> groups) {
        VBox page = new VBox(8);
        page.getStyleClass().add("page-content");

        Label total = app.mutedLabel("", 16);
        VBox results = new VBox();
        results.setMaxWidth(Double.MAX_VALUE);

        TextField search = app.searchField(app.userListGridView ? "搜索用户昵称或设备名称" : "搜索分组名或口令");
        search.setText(query);
        Button searchButton = app.iconToggleButton("mdi2m-magnify", "搜索", false);
        searchButton.setOnAction(event -> {
            query = search.getText() == null ? "" : search.getText();
            app.userListPage = 0;
            renderResults(devices, groups, results, total);
        });
        Button scan = app.primaryButton("扫描用户");
        scan.setOnAction(event -> app.showScanPage());
        Button groupAction = grouping ? app.primaryButton("确认分组") : app.secondaryButton("新建分组");
        groupAction.setOnAction(event -> {
            if (grouping) {
                saveSelectedGroup(groupName, groupCode);
            } else {
                enterGrouping();
            }
        });
        HBox scanLine = new HBox(12, search, searchButton, scan, groupAction);
        scanLine.setAlignment(Pos.CENTER_LEFT);
        if (grouping) {
            Button cancel = app.secondaryButton("取消");
            cancel.setOnAction(event -> cancelGrouping());
            scanLine.getChildren().add(cancel);
            scanLine.getChildren().addAll(groupFields());
        }
        StackPane scanHeader = new StackPane(scanLine);
        scanHeader.setAlignment(Pos.CENTER_LEFT);
        scanHeader.setMinHeight(46);
        scanHeader.setMaxWidth(Double.MAX_VALUE);

        Button listView = app.iconToggleButton("mdi2v-view-list", "列表形布局", !app.userListGridView);
        listView.setDisable(grouping);
        listView.setOnAction(event -> {
            app.userListGridView = false;
            app.userListPage = 0;
            showUserListPage();
        });
        Button gridView = app.iconToggleButton("mdi2v-view-grid", "矩阵形布局", app.userListGridView);
        gridView.setOnAction(event -> {
            app.userListGridView = true;
            app.userListPage = 0;
            showUserListPage();
        });
        HBox totalLine = new HBox(12, total, new HBox(8, listView, gridView));
        totalLine.setAlignment(Pos.CENTER_LEFT);

        search.textProperty().addListener((unused, oldValue, newValue) -> {
            query = newValue == null ? "" : newValue;
            app.userListPage = 0;
            renderResults(devices, groups, results, total);
        });
        page.getChildren().addAll(scanHeader, app.separator(), totalLine, results);
        renderResults(devices, groups, results, total);
        app.setMainPage("用户列表", page, true, true);
    }

    // 构建建组状态下的组名和默认口令输入框
    private List<Node> groupFields() {
        TextField name = app.textField("请输入分组名");
        name.setText(groupName);
        name.setPrefWidth(160);
        name.textProperty().addListener((unused, oldValue, newValue) -> groupName = newValue == null ? "" : newValue);
        TextField code = app.textField("请输入默认口令，无则留空");
        code.setText(groupCode);
        code.setPrefWidth(230);
        code.textProperty().addListener((unused, oldValue, newValue) -> groupCode = newValue == null ? "" : newValue);
        return List.of(name, code);
    }

    // 进入新建分组状态
    private void enterGrouping() {
        grouping = true;
        groupName = "";
        groupCode = "";
        groupDraft.clear();
        app.userListGridView = true;
        app.userListPage = 0;
        showUserListPage();
    }

    // 取消新建分组状态
    private void cancelGrouping() {
        grouping = false;
        groupName = "";
        groupCode = "";
        groupDraft.clear();
        showUserListPage();
    }

    // 把当前勾选的真实用户保存为分组
    private void saveSelectedGroup(String name, String code) {
        groupName = name == null ? "" : name.trim();
        groupCode = code == null ? "" : code.trim();
        if (groupName.isBlank()) {
            app.toast("请输入分组名");
            return;
        }
        List<UserDevice> members = groupDraft.stream()
                .filter(target -> !target.groupTarget())
                .toList();
        if (members.isEmpty()) {
            app.toast("请选择分组用户");
            return;
        }
        app.controller.saveGroup(groupName, groupCode, members).thenAccept(group -> Platform.runLater(() -> {
            grouping = false;
            groupDraft.clear();
            app.userListGridView = false;
            app.userListPage = 0;
            app.toast("已创建分组：" + groupName);
            showUserListPage();
        })).exceptionally(error -> {
            Platform.runLater(() -> app.toast("创建分组失败"));
            return null;
        });
    }

    // 按当前搜索词渲染用户或分组结果
    private void renderResults(List<UserDevice> devices, List<Group> groups, VBox results, Label total) {
        if (app.userListGridView) {
            List<UserDevice> filtered = devices.stream().filter(this::userMatches).toList();
            total.setText("共 " + filtered.size() + " 个用户");
            results.getChildren().setAll(userGrid(filtered));
            return;
        }
        List<Group> filtered = groups.stream().filter(this::groupMatches).toList();
        total.setText("共 " + filtered.size() + " 个分组");
        results.getChildren().setAll(groupList(filtered));
    }

    // 判断用户是否命中当前搜索词
    private boolean userMatches(UserDevice device) {
        String key = query.trim().toLowerCase(Locale.ROOT);
        if (key.isBlank()) {
            return true;
        }
        return device.nickname().toLowerCase(Locale.ROOT).contains(key)
                || device.deviceName().toLowerCase(Locale.ROOT).contains(key);
    }

    // 判断分组是否命中当前搜索词
    private boolean groupMatches(Group group) {
        String key = query.trim().toLowerCase(Locale.ROOT);
        if (key.isBlank()) {
            return true;
        }
        return group.name().toLowerCase(Locale.ROOT).contains(key)
                || group.code().toLowerCase(Locale.ROOT).contains(key);
    }

    // 构建分组列表布局
    private Node groupList(List<Group> groups) {
        VBox list = new VBox(10);
        list.setMaxWidth(Double.MAX_VALUE);
        groups.forEach(group -> list.getChildren().add(groupCard(group)));
        return list;
    }

    // 构建分组卡片
    private Node groupCard(Group group) {
        VBox card = new VBox(12);
        card.getStyleClass().add("user-card-large");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox top = new HBox(14);
        top.setAlignment(Pos.CENTER_LEFT);
        boolean editing = group.name().equals(editingGroup);
        TextField nameInput;
        TextField codeInput;
        VBox text;
        if (editing) {
            nameInput = app.textField("分组名");
            nameInput.setText(group.name());
            codeInput = app.textField("默认口令");
            codeInput.setText(group.code());
            text = new VBox(8, nameInput, codeInput);
        } else {
            nameInput = null;
            codeInput = null;
            String subtitle = "共" + group.size() + "名用户"
                    + (group.code().isBlank() ? "" : " | 口令：" + group.code());
            text = new VBox(6, app.titleLabel(group.name(), 22), app.mutedLabel(subtitle, 14));
        }
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        Button send = app.iconToggleButton("mdi2s-send", "发送", false);
        send.setOnAction(event -> {
            app.addRecentTarget(group.target());
            app.showFileTransferPage();
        });
        Button edit = app.iconToggleButton(editing ? "mdi2c-check" : "mdi2p-pencil",
                editing ? "保存" : "编辑", false);
        if (editing) {
            edit.getStyleClass().removeAll("secondary-button", "outline-button");
            edit.getStyleClass().add("primary-button");
        }
        edit.setOnAction(event -> {
            if (editing) {
                saveGroupEdit(group, nameInput.getText(), codeInput.getText());
            } else {
                editingGroup = group.name();
                showUserListPage();
            }
        });
        boolean expanded = expandedGroups.contains(group.name());
        Button fold = app.iconToggleButton(expanded ? "mdi2c-chevron-up" : "mdi2c-chevron-down",
                expanded ? "收起" : "展开", false);
        fold.setOnAction(event -> {
            if (expanded) {
                expandedGroups.remove(group.name());
            } else {
                expandedGroups.add(group.name());
            }
            showUserListPage();
        });
        HBox actions = new HBox(8, send, edit, fold);
        actions.setAlignment(Pos.CENTER_RIGHT);
        top.getChildren().addAll(text, actions);
        card.getChildren().add(top);
        if (expanded) {
            card.getChildren().add(memberGrid(group.members()));
        }
        return card;
    }

    // 保存分组卡片编辑内容
    private void saveGroupEdit(Group group, String name, String code) {
        String cleanName = name == null ? "" : name.trim();
        String cleanCode = code == null ? "" : code.trim();
        if (cleanName.isBlank()) {
            app.toast("请输入分组名");
            return;
        }
        app.controller.updateGroup(group.name(), cleanName, cleanCode, group.members()).thenAccept(target -> Platform.runLater(() -> {
            expandedGroups.remove(group.name());
            expandedGroups.add(cleanName);
            editingGroup = "";
            app.toast("分组已保存");
            showUserListPage();
        })).exceptionally(error -> {
            Platform.runLater(() -> app.toast("分组保存失败"));
            return null;
        });
    }

    // 构建分组内成员矩阵
    private Node memberGrid(List<UserDevice> devices) {
        GridPane grid = app.cardGrid(3, 10, 10);
        for (int i = 0; i < devices.size(); i++) {
            app.addCard(grid, app.userCard(devices.get(i), true), i, 3);
        }
        return grid;
    }

    // 构建用户矩阵分页布局
    private Node userGrid(List<UserDevice> devices) {
        int pageSize = 15;
        int maxPage = Math.max(0, (devices.size() - 1) / pageSize);
        app.userListPage = Math.min(app.userListPage, maxPage);
        int from = app.userListPage * pageSize;
        int to = Math.min(devices.size(), from + pageSize);

        GridPane grid = app.cardGrid(3, 14, 12);
        for (int i = from; i < to; i++) {
            app.addCard(grid, userCard(devices.get(i)), i - from, 3);
        }

        Button previous = app.secondaryButton("上一页");
        previous.setDisable(app.userListPage == 0);
        previous.setOnAction(event -> {
            app.userListPage--;
            showUserListPage();
        });
        Button next = app.secondaryButton("下一页");
        next.setDisable(app.userListPage >= maxPage);
        next.setOnAction(event -> {
            app.userListPage++;
            showUserListPage();
        });
        HBox pages = new HBox(12, previous, app.mutedLabel((app.userListPage + 1) + " / " + (maxPage + 1), 14), next);
        pages.setAlignment(Pos.CENTER);
        return new VBox(18, grid, pages);
    }

    // 构建用户卡片或建组勾选卡片
    private Node userCard(UserDevice device) {
        if (!grouping) {
            return app.userCard(device, true);
        }
        StackPane wrap = new StackPane(app.userCard(device, true, true));
        CheckBox check = app.checkBox("", inDraft(device));
        check.setOnAction(event -> setDraft(device, check.isSelected()));
        StackPane.setAlignment(check, Pos.CENTER_RIGHT);
        StackPane.setMargin(check, new Insets(0, 18, 0, 0));
        wrap.getChildren().add(check);
        return wrap;
    }

    // 判断建组草稿中是否已有该用户
    private boolean inDraft(UserDevice device) {
        return groupDraft.stream().anyMatch(item -> item.id().equals(device.id()));
    }

    // 更新建组草稿中的用户选择状态
    private void setDraft(UserDevice device, boolean selected) {
        groupDraft.removeIf(item -> item.id().equals(device.id()));
        if (selected) {
            groupDraft.add(device);
        }
    }
}
