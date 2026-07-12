package com.iwmei.lantransfer.view;
import com.iwmei.lantransfer.model.TransferFile;
import com.iwmei.lantransfer.model.TransferSummary;
import com.iwmei.lantransfer.model.TransferTask;
import com.iwmei.lantransfer.model.UserDevice;
import com.iwmei.lantransfer.util.FileIcons;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
final class FileTransfer {
    private final MainWindow app;
    FileTransfer(MainWindow app) {
        this.app = app;
    }
    void showFileTransferPage() {
        app.controller.loadRecentDevices().thenAccept(devices -> Platform.runLater(() -> {
            if (!app.recentTargetsLoaded) {
                app.recentTargets.addAll(devices);
                app.recentTargetsLoaded = true;
            }
            VBox page = new VBox(8);
            page.getStyleClass().add("page-content");
            List<TransferTask> tasks = app.currentSummary == null ? List.of() : app.currentSummary.tasks();
            page.getChildren().addAll(uploadStrip(), recentTargetsSection(app.recentTargets), transferListSection(tasks));
            app.setMainPage("文件传输", page, true, true);
        }));
    }
    void showTransferResultPage() {
        if (app.currentSummary == null) {
            startTransfer();
            return;
        }
        VBox page = new VBox(8);
        page.getStyleClass().add("page-content");
        page.getChildren().addAll(uploadStrip(), resultSummarySection(app.currentSummary), transferLogSection(app.currentSummary));
        app.setMainPage("文件传输", page, true, true);
    }
    private VBox uploadStrip() {
        VBox strip = app.glassSection("");
        strip.getStyleClass().add("upload-strip");
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_LEFT);
        Button upload = app.primaryButton("上传文件");
        upload.setOnAction(event -> chooseFiles());
        Button chooseFolder = app.primaryButton("选择文件夹");
        chooseFolder.setOnAction(event -> chooseFolder());
        row.getChildren().addAll(upload, chooseFolder, app.mutedLabel(uploadHint(), 14), app.spacer());
        if (!app.pendingFiles.isEmpty()) {
            Button start = app.outlineButton("开始发送");
            start.setDisable(app.transferRunning);
            start.setOnAction(event -> startTransfer());
            Button clear = app.ghostTextButton("全部清除");
            clear.setDisable(app.transferRunning);
            clear.setOnAction(event -> {
                app.pendingFiles.clear();
                showFileTransferPage();
            });
            row.getChildren().addAll(start, clear);
        }
        if (app.transferRunning) {
            row.getChildren().add(pauseButton());
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
        if (!app.pendingFiles.isEmpty()) {
            GridPane cards = app.cardGrid(2, 8, 8);
            cards.getStyleClass().add("pending-file-list");
            for (int i = 0; i < app.pendingFiles.size(); i++) {
                app.addCard(cards, pendingFileCard(app.pendingFiles.get(i)), i, 2);
            }
            strip.getChildren().add(cards);
        }
        return strip;
    }
    private String uploadHint() {
        return app.pendingFiles.isEmpty() ? "或拖拽到此处" : "已选择 " + app.pendingFiles.size() + " 个待传输项";
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
        Label name = app.titleLabel(file.fileName(), 15);
        name.setWrapText(true);
        name.setMaxWidth(Double.MAX_VALUE);
        VBox text = new VBox(4, name, app.mutedLabel(FileIcons.typeLabel(file.path()) + " | " + file.size(), 13),
                app.mutedLabel(FileIcons.modifiedAtLabel(file.path()), 12));
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        Button remove = app.compactButton("-");
        remove.setTooltip(new Tooltip("从待传输项移除"));
        remove.setOnAction(event -> {
            app.pendingFiles.remove(file);
            showFileTransferPage();
        });
        card.getChildren().addAll(fileIcon(file.path()), text, remove);
        return card;
    }
    private Node fileIcon(Path path) {
        FontIcon icon = new FontIcon(FileIcons.iconLiteral(path));
        icon.getStyleClass().add("file-card-font-icon");
        icon.setIconSize(24);
        StackPane box = new StackPane(icon);
        box.getStyleClass().add("file-icon-box");
        box.setMinSize(44, 44);
        box.setMaxSize(44, 44);
        return box;
    }
    private VBox recentTargetsSection(List<UserDevice> devices) {
        VBox section = app.glassSection("传输对象");
        GridPane cards = app.cardGrid(5, 8, 8);
        for (int i = 0; i < devices.size(); i++) {
            app.addCard(cards, app.userCard(devices.get(i), false), i, 5);
        }
        section.getChildren().add(cards);
        return section;
    }
    private VBox transferListSection(List<TransferTask> tasks) {
        VBox section = app.glassSection("");
        HBox header = app.sectionHeader("传输列表", null);
        Button clearCompleted = app.ghostTextButton("清除已完成");
        long running = tasks.stream().filter(task -> "传输中".equals(task.status())).count();
        long completed = tasks.stream().filter(task -> "已完成".equals(task.status())).count();
        long failed = tasks.stream().filter(task -> task.status().contains("失败")).count();
        clearCompleted.setDisable(completed == 0 || app.currentSummary == null);
        clearCompleted.setOnAction(event -> clearCompletedTasks());
        header.getChildren().addAll(app.tabPill("全部", String.valueOf(tasks.size()), true), app.tabPill("进行中", String.valueOf(running), false),
                app.tabPill("已完成", String.valueOf(completed), false), app.tabPill("已失败", String.valueOf(failed), false), app.spacer(), clearCompleted);
        section.getChildren().add(header);
        GridPane table = app.tableGrid("文件名", "目标对象", "进度", "大小", "速度", "时间", "状态", "操作");
        for (int i = 0; i < tasks.size(); i++) {
            app.addTransferRow(table, i + 1, tasks.get(i));
        }
        section.getChildren().add(table);
        return section;
    }
    private VBox resultSummarySection(TransferSummary summary) {
        VBox section = app.glassSection("传输结果");
        HBox stats = new HBox(12, app.statCard("目标总数", String.valueOf(summary.targetCount()), "#4f7bd8", "总"),
                app.statCard("成功", String.valueOf(summary.successCount()), "#2ecc40", "成"),
                app.statCard("失败", String.valueOf(summary.failedCount()), "#ff5353", "败"),
                app.statCard("重试", String.valueOf(summary.retryCount()), "#ffb22c", "重"));
        section.getChildren().addAll(stats, transferListSection(summary.tasks()));
        return section;
    }
    private VBox transferLogSection(TransferSummary summary) {
        VBox section = app.glassSection("传输日志");
        HBox header = new HBox(12, app.mutedLabel("耗时 " + summary.elapsed(), 14), app.spacer());
        Button clear = app.secondaryButton("清空日志");
        clear.setDisable(summary.logs().isEmpty());
        clear.setOnAction(event -> clearLogs());
        ToggleButton autoScroll = new ToggleButton();
        autoScroll.getStyleClass().add("switch-toggle");
        header.getChildren().addAll(app.mutedLabel("自动滚动", 14), autoScroll, clear);
        VBox logBox = new VBox(6);
        logBox.getStyleClass().add("log-box");
        summary.logs().forEach(line -> logBox.getChildren().add(app.logLine(line)));
        ScrollPane logScroll = new ScrollPane(logBox);
        logScroll.getStyleClass().add("log-scroll");
        section.getChildren().addAll(header, logScroll);
        return section;
    }
    private Button pauseButton() {
        Button pause = app.secondaryButton(app.transferPaused ? "继续发送" : "暂停发送");
        pause.setOnAction(event -> togglePause());
        return pause;
    }
    private void clearCompletedTasks() {
        if (app.currentSummary == null) {
            return;
        }
        app.currentSummary = app.currentSummary.withoutCompleted();
        showTransferResultPage();
    }
    private void clearLogs() {
        if (app.currentSummary == null) {
            return;
        }
        app.currentSummary = app.currentSummary.withoutLogs();
        showTransferResultPage();
    }
    private void startTransfer() {
        if (app.pendingFiles.isEmpty()) {
            app.toast("请先选择要上传的文件或文件夹");
            return;
        }
        if (app.transferRunning) {
            app.toast("当前已有发送任务");
            return;
        }
        List<UserDevice> targets = app.selectedTargets.isEmpty() ? new ArrayList<>(app.recentTargets) : new ArrayList<>(app.selectedTargets);
        if (!hasUsableTarget(targets)) {
            app.toast("请先从用户列表添加真实在线用户或分组");
            return;
        }
        Optional<String> code = askTransferCode(targets);
        if (code.isEmpty()) {
            return;
        }
        app.controller.pauseTransfer(false);
        app.transferRunning = true;
        app.transferPaused = false;
        app.currentSummary = new TransferSummary(targets.size(), 0, 0, 0, "00:00:00", List.of(), List.of());
        showTransferResultPage();
        app.controller.startTransfer(new ArrayList<>(app.pendingFiles), targets, code.get(),
                summary -> Platform.runLater(() -> showTransferProgress(summary)))
                .thenAccept(summary -> Platform.runLater(() -> {
                    app.transferRunning = false;
                    app.transferPaused = false;
                    app.playDoneSound();
                    showTransferProgress(summary);
                })).exceptionally(error -> {
                    Platform.runLater(() -> {
                        app.transferRunning = false;
                        app.transferPaused = false;
                        app.toast("发送任务异常结束");
                        showTransferResultPage();
                    });
                    return null;
        });
    }
    private boolean hasUsableTarget(List<UserDevice> targets) {
        return targets.stream().anyMatch(target -> target != null && (target.groupTarget() || target.reachable()));
    }
    private Optional<String> askTransferCode(List<UserDevice> targets) {
        TextInputDialog dialog = new TextInputDialog(defaultGroupCode(targets));
        dialog.initOwner(app.stage);
        dialog.setTitle("传输口令");
        dialog.setHeaderText("本次传输口令");
        dialog.setContentText("无口令请留空");
        return dialog.showAndWait().map(String::trim);
    }
    private String defaultGroupCode(List<UserDevice> targets) {
        return targets.stream()
                .filter(target -> target != null)
                .filter(UserDevice::groupTarget)
                .map(UserDevice::signature)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElse("");
    }
    private void togglePause() {
        app.transferPaused = !app.transferPaused;
        app.controller.pauseTransfer(app.transferPaused);
        app.toast(app.transferPaused ? "已暂停发送" : "已继续发送");
        showTransferResultPage();
    }
    private void showTransferProgress(TransferSummary summary) {
        app.currentSummary = summary;
        showTransferResultPage();
    }
    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要上传的文件");
        List<File> files = chooser.showOpenMultipleDialog(app.stage);
        if (files != null && !files.isEmpty()) {
            addFiles(files);
        }
    }
    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择要上传的文件夹");
        File folder = chooser.showDialog(app.stage);
        if (folder != null) {
            app.pendingFiles.add(new TransferFile(folder.getName(), FileIcons.readableSize(folder), folder.toPath()));
            showFileTransferPage();
        }
    }
    private void addFiles(List<File> files) {
        int skipped = 0;
        for (File file : files) {
            if (FileIcons.supported(file.toPath())) {
                app.pendingFiles.add(new TransferFile(file.getName(), FileIcons.readableSize(file), file.toPath()));
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            app.toast("已跳过不支持的文件类型：" + skipped + "个");
        }
        showFileTransferPage();
    }
}
