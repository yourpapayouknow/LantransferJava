package com.iwmei.lantransfer.model;

// 单个传输任务数据对象
public record TransferTask(String fileName, UserDevice target, int progressPercent, String size, String speed,
                           String elapsed, String status, int retries) {
}
