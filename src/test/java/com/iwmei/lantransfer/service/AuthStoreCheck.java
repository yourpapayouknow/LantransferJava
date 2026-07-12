package com.iwmei.lantransfer.service;
import com.iwmei.lantransfer.model.AuthResult;
import com.iwmei.lantransfer.model.LoginRequest;
import com.iwmei.lantransfer.model.Profile;
import com.iwmei.lantransfer.model.RegisterRequest;
import com.iwmei.lantransfer.model.UserStatus;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
public final class AuthStoreCheck {
    private AuthStoreCheck() {
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("lantransfer-auth-check");
        try {
            Path acco = dir.resolve("acco");
            AuthStore store = new AuthStore(acco, dir.resolve("la"), dir.resolve("req"), false);
            AuthResult admin = store.login(new LoginRequest("admin", "admin", false));
            require(!admin.success(), "empty acco should not login admin");
            AuthResult registered = store.register(new RegisterRequest("alice", "secret", "LAPTOP-A"));
            require(registered.success(), "registered account should succeed");
            require(!registered.pendingReview(), "disabled git sync should write acco directly");
            String table = Files.readString(acco);
            require(table.startsWith("account,salt,hash"), "acco should have table header");
            require(table.contains("alice"), "acco should contain registered account");
            require(table.contains("AUTO_APPROVED"), "registration should auto approve");
            require(table.contains("actions"), "actions approver should be recorded");
            require(!table.contains("secret"), "acco should not contain plaintext password");
            AuthResult duplicate = store.register(new RegisterRequest("alice", "secret", "LAPTOP-A"));
            require(!duplicate.success(), "duplicate account should fail");
            AuthResult wrong = store.login(new LoginRequest("alice", "bad", false));
            require(!wrong.success(), "wrong password should fail");
            AuthResult ok = store.login(new LoginRequest("alice", "secret", true));
            require(ok.success(), "registered account should login");
            require("alice".equals(ok.profile().nickname()), "profile should use account nickname");
            require("alice".equals(store.rememberedAccount()), "remembered account should persist");
            Profile renamed = new Profile("Alice", ok.profile().userId(), "LAPTOP-B", "新的状态",
                    ok.profile().registeredAt(), ok.profile().lastLoginAt(), ok.profile().version(), ok.profile().language(),
                    ok.profile().status(), "QUJD");
            store.updateProfile(renamed);
            store.updateStatus(UserStatus.BUSY, "忙碌中");
            AuthResult updated = store.login(new LoginRequest("alice", "secret", true));
            require("Alice".equals(updated.profile().nickname()), "profile update should persist nickname");
            require("QUJD".equals(updated.profile().avatar()), "profile update should persist avatar");
            require("忙碌中".equals(updated.profile().signature()), "status update should persist signature");
            require(updated.profile().status() == UserStatus.BUSY, "status update should persist status");
            store.login(new LoginRequest("alice", "secret", false));
            require(store.rememberedAccount().isBlank(), "remembered account should clear when unchecked");
            Method repoPath = AuthStore.class.getDeclaredMethod("repoPath", String.class);
            repoPath.setAccessible(true);
            require("owner/repo".equals(repoPath.invoke(store, "https://github.com/owner/repo.git")),
                    "https remote should parse owner and repo");
            require("owner/repo".equals(repoPath.invoke(store, "git@github.com:owner/repo.git")),
                    "ssh remote should parse owner and repo");
            System.setProperty("acco.t", "tok/en");
            Method clean = AuthStore.class.getDeclaredMethod("clean", String.class);
            clean.setAccessible(true);
            require(!((String) clean.invoke(store, "failed tok/en tok%2Fen")).contains("tok"),
                    "git output should hide token");
            System.clearProperty("acco.t");
        } finally {
            delete(dir);
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
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
