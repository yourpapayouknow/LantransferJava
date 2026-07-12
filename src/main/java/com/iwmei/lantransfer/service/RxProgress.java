package com.iwmei.lantransfer.service;
@FunctionalInterface
public interface RxProgress {
    void update(String fileName, int percent);
}
