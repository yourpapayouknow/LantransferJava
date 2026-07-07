package com.iwmei.lantransfer.service;

import com.iwmei.lantransfer.model.AuthResult;
import com.iwmei.lantransfer.model.LoginRequest;
import com.iwmei.lantransfer.model.RegisterRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

// AuthStore 的无框架自检入口
public final class AuthStoreCheck {
    // 阻止自检类被实例化
    private AuthStoreCheck() {
    }

    // 运行注册、重复注册、错误密码和正确登录检查
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("lantransfer-auth-check");
        try {
            AuthStore store = new AuthStore(dir.resolve("users.properties"));
            AuthResult admin = store.login(new LoginRequest("admin", "admin", false));
            require(admin.success(), "default admin should login");

            AuthResult registered = store.register(new RegisterRequest("alice", "secret", "LAPTOP-A"));
            require(registered.success(), "registered account should succeed");
            require(!registered.pendingReview(), "local registration should not wait for review");

            AuthResult duplicate = store.register(new RegisterRequest("alice", "secret", "LAPTOP-A"));
            require(!duplicate.success(), "duplicate account should fail");

            AuthResult wrong = store.login(new LoginRequest("alice", "bad", false));
            require(!wrong.success(), "wrong password should fail");

            AuthResult ok = store.login(new LoginRequest("alice", "secret", true));
            require(ok.success(), "registered account should login");
            require("alice".equals(ok.profile().nickname()), "profile should use account nickname");
        } finally {
            delete(dir);
        }
    }

    // 断言条件为真
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // 删除临时自检目录
    private static void delete(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
