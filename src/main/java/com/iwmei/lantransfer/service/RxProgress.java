package com.iwmei.lantransfer.service;

// 接收进度回调，供接收端把文件接收百分比展示给界面
@FunctionalInterface
public interface RxProgress {
    // 推送指定文件的接收进度
    void update(String fileName, int percent);
}
