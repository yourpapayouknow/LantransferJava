# 极速互传 - LantransferJava

![Project badge](assets/readme-badge.png)

极速互传是一款基于 JavaFX 17 开发的轻量级局域网高速文件传输桌面应用。该项目旨在提供局域网内免除中心服务器的高效、安全、多目标的快速文件共享服务，底层结合了 UDP 多播/广播设备自动发现协议与多线程 UDP 可靠文件传输协议。

同时，本项目设计并实现了一种无服务器验证方案：利用 GitHub 仓库作为公共数据库，通过推送注册请求与 GitHub Actions 自动合入，实现了跨网络但无须运行传统后端服务的用户注册登录闭环体验。

---

## 核心特性

- **局域网设备发现**
  - 利用 UDP 广播机制，在局域网内主动同步与扫描同程序客户端。
  - 自动识别并呈现设备昵称、设备 ID、设备名、状态以及个性签名，提供动态刷新。

- **多目标多线程可靠 UDP 传输**
  - 支持一次性勾选多个目标设备，实现文件的并行分发传输。
  - 协议层使用自定义 BEGIN -> DATA -> ACK 可靠连接控制机制，配合分片并发提高带宽吞吐量。

- **分片缓存与断点续传**
  - 文件在发送与接收端均支持分片级缓存，可在因网络抖动中断后实现快速断点续传。
  - 传输完成后，自动计算并执行文件 SHA-256 完整性校验，确保数据零损坏。

- **口令验证与隐私控制**
  - **群组口令隔离**：配置群组口令后可进行设备可见度隔离，安全隐藏在非公共通道。
  - **发送前口令确认**：发送端可设定临时提取口令，接收端在匹配正确的口令后方可开始写入。
  - **忙碌拦截 (Busy Door)**：当本机状态设定为“忙碌”时，自动拦截来包并弹窗提醒用户进行手动接收确认。

- **传输控制与限速**
  - 支持对上传/下载速率进行动态控制（通过自适应 ACK 间隔时间控制延迟）。
  - 支持传输失败超时自动重试（默认 3 次），并生成详尽的传输结果报告。

- **系统深度集成**
  - 原生 Windows 系统托盘支持，最小化后驻留后台，防止因误关闭导致传输中断。
  - 支持 Windows 系统开机自启动设置。
  - 任务完成伴有悦耳的自定义提示音。

---

## 技术栈

- **核心语言与框架**：Java 17 / JavaFX 17 (OpenJFX)
- **构建工具**：Maven 3.6+
- **图标包**：Ikonli (Fluent UI & Material Design 2 混合 pack)
- **云账户机制**：Git / GitHub Actions Workflow

---

## 快速开始

### 1. 环境准备
确保您的运行环境中已安装：
* Java SDK 17 或更高版本。
* Maven 3.6 及以上版本。
* Windows 操作系统 (以支持开机自启动和 CMD 优化脚本)。

### 2. 账号系统激活 (首批用户登录/注册需要)
由于没有独立中心服务器，项目依靠 GitHub 仓库存储和验证账号。若需要注册新账号：
1. 申请一个具备 `repo` 权限的 GitHub Classic PAT (Personal Access Token)。
2. 运行根目录下的 `tok.ps1` 脚本，将 token 填入以实现本地加密保存：
   ```powershell
   # 在项目根目录下打开 PowerShell 执行
   .\tok.ps1
   ```
   该 token 将保存在 Windows 本地 AppData 目录，避免硬编码到代码中。

### 3. 构建与运行
* **安全拉取 Token 并运行 (推荐注册新账号时使用)**：
  使用 `run.ps1` 会自动提取已保存的 Token 注入环境变量并完成启动，并在退出后安全擦除。
  ```powershell
  .\run.ps1
  ```

* **独立运行 (本地已存在登录状态时)**：
  如果已有记住的账号或者直接进行本地测试，可以直接通过 Maven 命令行运行：
  ```bash
  mvn javafx:run
  ```

---

## 项目模块与结构

* **`src/main/java/com/iwmei/lantransfer/App.java`**：应用最轻量入口，负责拉起 JavaFX 主页面。
* **`src/main/java/com/iwmei/lantransfer/view/`**：JavaFX 界面与窗口组件，包括主界面、设置面板、用户列表和我的资料卡。
* **`src/main/java/com/iwmei/lantransfer/controller/`**：事件响应控制层，调用后端的 BackendFacade 桥接 UI 与业务。
* **`src/main/java/com/iwmei/lantransfer/service/`**：
  * `AuthStore.java`：GitHub 仓库账号数据同步与注册。
  * `UdpTx.java` / `UdpRx.java`：UDP 发送与接收传输核心协议。
  * `LanPeer.java`：局域网设备扫描与广播宣告。
* **`acco`**：用作简易数据库的账号表（CSV 格式）。
* **`req/`**：用于存放待合入的临时注册请求。

---

## 设计规范与文档

关于代码各模块的设计细节与未尽事宜，可参阅：
* [JAVA 功能说明书](file:///f:/College/Activ_CodeProjects/IdeaProjects/Class_JiSuanJiWangLuoDaZuoYe/JAVA功能说明书.md)：详尽的类与接口设计规范。
* [未实现功能清单0](file:///f:/College/Activ_CodeProjects/IdeaProjects/Class_JiSuanJiWangLuoDaZuoYe/未实现功能清单0.md)：暂时跳过或未实现的特性账本。
