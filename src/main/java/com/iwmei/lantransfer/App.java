package com.iwmei.lantransfer;
import com.iwmei.lantransfer.view.MainWindow;
import javafx.application.Application;

// 应用启动类，提供Maven/JavaFX主入口
public class App {
    // 程序启动入口，交给JavaFX加载主窗口
    public static void main(String[] args) {
        Application.launch(MainWindow.class, args);
    }
}
