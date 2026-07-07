package com.iwmei.lantransfer.view;

import com.iwmei.lantransfer.model.UserDevice;
import com.iwmei.lantransfer.util.DeviceSearch;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

// 用户列表页面逻辑
final class UserList {
    private final MainWindow app;
    private String query = "";

    // 初始化用户列表页面对象
    UserList(MainWindow app) {
        this.app = app;
    }

    // 显示用户列表页面
    void showUserListPage() {
        app.controller.loadAllDevices().thenAccept(devices -> Platform.runLater(() -> {
            VBox page = new VBox(8);
            page.getStyleClass().add("page-content");

            TextField search = app.searchField("搜索用户昵称或设备 ID");
            search.setText(query);
            Button scan = app.primaryButton("扫描用户");
            scan.setOnAction(event -> app.showScanPage());
            Label lastScan = app.mutedLabel("上次扫描： 刚刚", 14);
            HBox scanLine = new HBox(12, search, scan, lastScan);
            scanLine.setAlignment(Pos.CENTER_LEFT);
            StackPane scanHeader = new StackPane(scanLine);
            scanHeader.setAlignment(Pos.CENTER_LEFT);
            scanHeader.setMinHeight(46);
            scanHeader.setMaxWidth(Double.MAX_VALUE);

            Button listView = app.iconToggleButton("mdi2v-view-list", "列表形布局", !app.userListGridView);
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
            Label total = app.mutedLabel("", 16);
            HBox totalLine = new HBox(12, total, new HBox(8, listView, gridView));
            totalLine.setAlignment(Pos.CENTER_LEFT);

            VBox results = new VBox();
            results.setMaxWidth(Double.MAX_VALUE);
            search.textProperty().addListener((unused, oldValue, newValue) -> {
                query = newValue == null ? "" : newValue;
                app.userListPage = 0;
                renderResults(devices, results, total);
            });
            page.getChildren().addAll(scanHeader, app.separator(), totalLine, results);
            renderResults(devices, results, total);
            app.setMainPage("用户列表", page, true, true);
        }));
    }

    // 按当前搜索词渲染用户列表结果
    private void renderResults(List<UserDevice> devices, VBox results, Label total) {
        List<UserDevice> filtered = devices.stream().filter(device -> DeviceSearch.matches(device, query)).toList();
        total.setText("共 " + filtered.size() + " 个用户");
        results.getChildren().setAll(app.userListGridView ? userGrid(filtered) : userList(filtered));
    }

    // 构建用户列表布局
    private Node userList(List<UserDevice> devices) {
        VBox list = new VBox(10);
        list.setMaxWidth(Double.MAX_VALUE);
        devices.forEach(device -> list.getChildren().add(app.userCard(device, true)));
        return list;
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
            app.addCard(grid, app.userCard(devices.get(i), true), i - from, 3);
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
}
