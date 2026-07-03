package com.iwmei.lantransfer.model;

import java.nio.file.Path;

public record TransferFile(String fileName, String size, Path path) {
}
