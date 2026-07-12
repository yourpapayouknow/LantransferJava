package com.iwmei.lantransfer.model;
import java.nio.file.Path;

// 待传输文件数据对象
public record TransferFile(String fileName, String size, Path path) {
}
