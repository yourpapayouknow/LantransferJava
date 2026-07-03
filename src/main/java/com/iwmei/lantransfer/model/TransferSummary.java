package com.iwmei.lantransfer.model;

import java.util.List;

public record TransferSummary(int targetCount, int successCount, int failedCount, int retryCount, String elapsed,
                              List<String> logs, List<TransferTask> tasks) {
}
