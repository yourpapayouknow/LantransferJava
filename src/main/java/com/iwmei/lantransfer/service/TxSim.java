package com.iwmei.lantransfer.service;
import com.iwmei.lantransfer.model.DeviceStatus;
import com.iwmei.lantransfer.model.TransferFile;
import com.iwmei.lantransfer.model.TransferSummary;
import com.iwmei.lantransfer.model.TransferTask;
import com.iwmei.lantransfer.model.UserDevice;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 本地传输模拟器，负责在UDP内核完成前生成真实文件和目标维度的结果报告
final class TxSim {
    private static final long SPEED_BYTES = 12L * 1024 * 1024;
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter DONE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 根据待传输文件和目标设备生成传输汇总
    TransferSummary run(List<TransferFile> files, List<UserDevice> targets) {
        List<TransferFile> safeFiles = files == null ? List.of() : files;
        List<UserDevice> safeTargets = targets == null ? List.of() : targets;
        long totalBytes = totalBytes(safeFiles);
        int success = (int) safeTargets.stream().filter(this::online).count();
        int failed = safeTargets.size() - success;
        int retries = failed * 3;
        List<TransferTask> tasks = tasks(safeFiles, safeTargets);
        return new TransferSummary(safeTargets.size(), success, failed, retries, elapsed(totalBytes),
                logs(safeFiles, safeTargets, totalBytes, success, failed, retries), tasks);
    }

    // 构造传输任务列表
    private List<TransferTask> tasks(List<TransferFile> files, List<UserDevice> targets) {
        List<TransferTask> tasks = new ArrayList<>();
        String doneAt = DONE_TIME.format(LocalDateTime.now());
        for (TransferFile file : files) {
            long bytes = sizeOf(file.path());
            for (UserDevice target : targets) {
                if (online(target)) {
                    tasks.add(new TransferTask(file.fileName(), target, 100, readable(bytes), speed(), doneAt, "已完成", 0));
                } else {
                    tasks.add(new TransferTask(file.fileName(), target, 0, readable(bytes), "-", doneAt, "传输失败", 3));
                }
            }
        }
        return tasks;
    }

    // 构造传输日志
    private List<String> logs(List<TransferFile> files, List<UserDevice> targets, long totalBytes, int success, int failed, int retries) {
        List<String> logs = new ArrayList<>();
        logs.add(stamp("任务开始：共 " + targets.size() + " 个目标，文件总数 " + files.size() + " 个，大小 " + readable(totalBytes)));
        for (UserDevice target : targets) {
            if (online(target)) {
                logs.add(stamp("✓ [" + target.nickname() + "(" + target.deviceName() + ")] 本地模拟发送完成 (" + files.size() + "/" + files.size() + ")"));
            } else {
                logs.add(stamp("⚠ [" + target.nickname() + "(" + target.deviceName() + ")] 设备离线，已重试 3 次后失败"));
            }
        }
        logs.add(stamp("任务结束：成功 " + success + "，失败 " + failed + "，重试 " + retries));

        // ponytail: local simulation until the UDP sender exists; replace this class when wire transfer is implemented.
        return logs;
    }

    // 判断目标是否在线可发送
    private boolean online(UserDevice target) {
        return target != null && target.status() == DeviceStatus.ONLINE;
    }

    // 汇总所有待传输文件大小
    private long totalBytes(List<TransferFile> files) {
        long total = 0;
        for (TransferFile file : files) {
            total += sizeOf(file.path());
        }
        return total;
    }

    // 读取文件或文件夹大小
    private long sizeOf(Path path) {
        if (path == null || !Files.exists(path)) {
            return 0;
        }
        if (!Files.isDirectory(path)) {
            return fileSize(path);
        }
        try (var children = Files.walk(path)) {
            return children.filter(Files::isRegularFile).mapToLong(this::fileSize).sum();
        } catch (Exception ignored) {
            return 0;
        }
    }

    // 读取单个文件大小
    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ignored) {
            return 0;
        }
    }

    // 格式化传输耗时
    private String elapsed(long bytes) {
        long seconds = Math.max(1, (long) Math.ceil(bytes / (double) SPEED_BYTES));
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    // 格式化传输速度
    private String speed() {
        return readable(SPEED_BYTES) + "/s";
    }

    // 格式化字节大小
    private String readable(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.ROOT, "%.2f MB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024) {
            return String.format(Locale.ROOT, "%.2f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    // 给日志加时间戳
    private String stamp(String message) {
        return "[" + LOG_TIME.format(LocalTime.now()) + "]  " + message;
    }
}
