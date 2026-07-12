package com.iwmei.lantransfer.service;
@FunctionalInterface
public interface RxAsk {
    boolean approve(String fileName, long bytes, String codeHash);
}
