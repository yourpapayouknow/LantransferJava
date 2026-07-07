package com.iwmei.lantransfer;

import com.iwmei.lantransfer.view.MainWindow;
import javafx.application.Application;

// 应用启动类，提供 Maven/JavaFX 主入口
public class App {
    // 程序启动入口，交给 JavaFX 加载主窗口
    public static void main(String[] args) {
        Application.launch(MainWindow.class, args);
    }
}
