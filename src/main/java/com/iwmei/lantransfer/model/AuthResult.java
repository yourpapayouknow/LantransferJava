package com.iwmei.lantransfer.model;

public record AuthResult(boolean success, boolean pendingReview, String message, Profile profile) {
}
