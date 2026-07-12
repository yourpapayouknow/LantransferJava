package com.iwmei.lantransfer.model;

// 登录请求数据对象
public record LoginRequest(String account, String password, boolean rememberMe) {
}
