package com.iwmei.lantransfer.service;

// 接收前确认回调，供忙碌状态下询问用户是否接收文件
@FunctionalInterface
public interface RxAsk {
    // 返回是否允许接收指定文件
    boolean approve(String fileName, long bytes);
}
