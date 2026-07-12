package com.iwmei.lantransfer.model;

// 注册请求数据对象
public record RegisterRequest(String account, String password, String deviceName) {
}
