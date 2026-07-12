package com.iwmei.lantransfer.model;
import java.util.List;
public record TransferSummary(int targetCount, int successCount, int failedCount, int retryCount, String elapsed,
                              List<String> logs, List<TransferTask> tasks) {
    public TransferSummary withoutCompleted() {
        return with(logs, tasks.stream().filter(task -> !"已完成".equals(task.status())).toList());
    }
    public TransferSummary withoutLogs() {
        return new TransferSummary(targetCount, successCount, failedCount, retryCount, elapsed, List.of(), tasks);
    }
    private TransferSummary with(List<String> logs, List<TransferTask> tasks) {
        int targetCount = (int) tasks.stream().map(this::targetId).distinct().count();
        int success = (int) tasks.stream().filter(task -> "已完成".equals(task.status())).map(this::targetId).distinct().count();
        int failed = (int) tasks.stream().filter(task -> task.status().contains("失败")).map(this::targetId).distinct().count();
        int retries = tasks.stream().mapToInt(TransferTask::retries).sum();
        return new TransferSummary(targetCount, success, failed, retries, elapsed, logs, tasks);
    }
    private String targetId(TransferTask task) {
        return task.target() == null ? "" : task.target().id();
    }
}
