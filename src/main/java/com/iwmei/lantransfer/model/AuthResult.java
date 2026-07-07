package com.iwmei.lantransfer.model;

// 登录或注册结果数据对象
public record AuthResult(boolean success, boolean pendingReview, String message, Profile profile) {
}
