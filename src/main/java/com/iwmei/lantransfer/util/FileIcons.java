package com.iwmei.lantransfer.util;
import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

// 文件显示工具类，负责大小、日期和图标判断
public final class FileIcons {
    private static final DateTimeFormatter DATE_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> SUPPORTED = Set.of("pdf", "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg",
            "mp4", "mov", "mkv", "avi", "webm", "mp3", "wav", "flac", "aac", "ogg", "zip", "rar", "7z",
            "tar", "gz", "doc", "docx", "rtf", "txt", "md", "xls", "xlsx", "csv", "ppt", "pptx", "key",
            "java", "kt", "js", "ts", "jsx", "tsx", "py", "c", "cpp", "cs", "go", "rs", "html", "css",
            "xml", "json", "yml", "yaml", "bin", "prproj", "aep", "aepx", "psd", "ai", "xd", "indd");

    // 阻止工具类被外部实例化
    private FileIcons() {
    }

    // 生成文件修改日期显示文本
    public static String modifiedAtLabel(Path path) {
        if (path == null) {
            return "修改日期：-";
        }
        try {
            return "修改日期：" + DATE_MINUTE.format(Files.getLastModifiedTime(path).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime());
        } catch (Exception ignored) {
            return "修改日期：-";
        }
    }

    // 根据文件或文件夹类型选择图标
    public static String iconLiteral(Path path) {
        if (path != null && Files.isDirectory(path)) {
            // 根据文件夹内容判断项目类型图标
            return folderIcon(path);
        }
        String ext = extension(path == null ? "" : path.getFileName().toString());
        return switch (ext) {
            case "pdf" -> "fltral-document-pdf-24";
            case "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg" -> "fltral-image-24";
            case "mp4", "mov", "mkv", "avi", "webm" -> "fltrmz-video-24";
            case "mp3", "wav", "flac", "aac", "ogg" -> "fltrmz-music-note-24";
            case "zip", "rar", "7z", "tar", "gz" -> "fltral-archive-24";
            case "doc", "docx", "rtf", "txt", "md" -> "fltrmz-text-description-24";
            case "xls", "xlsx", "csv" -> "fltrmz-table-24";
            case "ppt", "pptx", "key" -> "fltrmz-slide-text-24";
            case "java", "kt", "js", "ts", "jsx", "tsx", "py", "c", "cpp", "cs", "go", "rs", "html", "css", "xml", "json", "yml", "yaml" -> "fltral-code-24";
            case "prproj", "aep", "aepx" -> "fltrmz-video-clip-24";
            case "psd", "ai", "xd", "indd" -> "fltrmz-paint-brush-24";
            default -> "fltral-document-24";
        };
    }

    // 判断路径是否属于当前允许传输的文件类型
    public static boolean supported(Path path) {
        return path != null && (Files.isDirectory(path) || supportedName(path.getFileName().toString()));
    }

    // 判断文件名是否属于当前允许传输的文件类型
    public static boolean supportedName(String name) {
        String ext = extension(name == null ? "" : name);
        return ext.isBlank() || SUPPORTED.contains(ext);
    }

    // 返回文件类型展示文本
    public static String typeLabel(Path path) {
        if (path != null && Files.isDirectory(path)) {
            return "文件夹";
        }
        String ext = extension(path == null ? "" : path.getFileName().toString());
        return switch (ext) {
            case "" -> "无扩展名";
            case "pdf" -> "PDF";
            case "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg" -> "图片";
            case "mp4", "mov", "mkv", "avi", "webm" -> "视频";
            case "mp3", "wav", "flac", "aac", "ogg" -> "音频";
            case "zip", "rar", "7z", "tar", "gz" -> "压缩包";
            case "doc", "docx", "rtf", "txt", "md" -> "文档";
            case "xls", "xlsx", "csv" -> "表格";
            case "ppt", "pptx", "key" -> "演示文稿";
            case "java", "kt", "js", "ts", "jsx", "tsx", "py", "c", "cpp", "cs", "go", "rs", "html", "css",
                 "xml", "json", "yml", "yaml" -> "代码";
            case "bin" -> "二进制";
            case "prproj", "aep", "aepx" -> "视频工程";
            case "psd", "ai", "xd", "indd" -> "设计工程";
            default -> "未知类型";
        };
    }

    // 把文件大小转换成界面可读文本
    public static String readableSize(File file) {
        if (file.isDirectory()) {
            return "文件夹";
        }
        long bytes = file.length();
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.ROOT, "%.2f MB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024) {
            return String.format(Locale.ROOT, "%.2f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    // 根据文件夹内容判断项目类型图标
    private static String folderIcon(Path folder) {
        // ponytail: direct children only; recurse later if folder icon accuracy matters more than drag speed.
        try (DirectoryStream<Path> children = Files.newDirectoryStream(folder)) {
            for (Path child : children) {
                String name = child.getFileName().toString().toLowerCase(Locale.ROOT);
                if (isAdobeVideoProject(name)) {
                    return "fltrmz-video-clip-24";
                }
                if (isAdobeDesignProject(name)) {
                    return "fltrmz-paint-brush-24";
                }
                if (isIdeProjectMarker(name)) {
                    return "fltral-app-folder-24";
                }
            }
        } catch (Exception ignored) {
            return "fltral-folder-24";
        }
        return "fltral-folder-24";
    }

    // 判断是否为Adobe视频工程文件
    private static boolean isAdobeVideoProject(String name) {
        return name.endsWith(".prproj") || name.endsWith(".aep") || name.endsWith(".aepx");
    }

    // 判断是否为Adobe设计工程文件
    private static boolean isAdobeDesignProject(String name) {
        return name.endsWith(".psd") || name.endsWith(".ai") || name.endsWith(".xd") || name.endsWith(".indd");
    }

    // 判断是否为常见IDE项目标记
    private static boolean isIdeProjectMarker(String name) {
        return name.equals(".idea") || name.equals(".vscode") || name.equals("pom.xml") || name.equals("build.gradle")
                || name.equals("settings.gradle") || name.equals("package.json") || name.equals("pyproject.toml")
                || name.equals("cargo.toml") || name.equals("go.mod") || name.endsWith(".sln")
                || name.endsWith(".csproj") || name.endsWith(".vcxproj") || name.endsWith(".xcodeproj");
    }

    // 提取文件扩展名用于类型判断
    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
