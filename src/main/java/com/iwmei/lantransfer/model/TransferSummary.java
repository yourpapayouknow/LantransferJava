package com.iwmei.lantransfer.model;

import java.util.List;

// 传输结果汇总数据对象
public record TransferSummary(int targetCount, int successCount, int failedCount, int retryCount, String elapsed,
                              List<String> logs, List<TransferTask> tasks) {
}
