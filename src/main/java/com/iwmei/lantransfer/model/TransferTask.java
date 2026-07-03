package com.iwmei.lantransfer.model;

public record TransferTask(String fileName, UserDevice target, int progressPercent, String size, String speed,
                           String elapsed, String status, int retries) {
}
