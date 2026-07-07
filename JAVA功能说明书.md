# Java 功能说明书

本文档是本项目唯一的 Java 类说明书。每次新增或修改 Java 文件，都要同步更新对应条目；如果功能暂不实现，记录到“功能跳过记录”。

## 后端实现顺序

1. 登录与注册：替换演示账号逻辑，提供无服务器环境下可用的账号存储、登录校验、注册记录和资料返回。
2. 文件传输首页：接入真实近期传输对象、待传输文件信息、传输任务创建和传输结果。
3. 用户列表：接入可发现设备、搜索、在线状态和近期对象维护。
4. 局域网扫描：实现广播或组播发现本局域网运行本程序的主机。
5. 我的资料：实现资料保存、状态切换、自定义状态和账号信息读取。
6. 系统设置：实现本机 IP、限速、重试次数、主题、字体、缩放、语言和启动项的读写。
7. 传输结果：实现 UDP 多线程发送、确认、三次重试、失败报告、日志、完整性校验、分片缓存、断点重传和剩余时间统计。
8. 特色扩展：按复杂度依次处理传输口令小群组、按用户状态条件传输、智能带宽分配等功能。

## 功能跳过记录

- 真实 UDP 多线程传输调度与实时进度：目标级并发 UDP 发送、ACK 等待、后台接收和接收目录落盘已由 `UdpTx/UdpRx` 实现；未实现的是传输中实时进度推送、接收端进度展示、队列调度和分片级并发。推荐替代方案：在现有 `UdpTx/UdpRx` 协议上增加任务状态仓库，把已确认分片数暴露给 UI；只有在大文件性能不足时再把单文件分片分派到并发 worker。
- 文件临时分片缓存与断点重传：`UdpRx` 已使用 `.part` 临时文件和内存 `BitSet` 记录当次接收分片，但分片状态没有持久化到磁盘，应用重启后不能继续；发送端也没有按接收端缺失分片列表定向补发。推荐替代方案：为每个任务保存 chunk bitmap 和元数据文件，重启后恢复 `.part` 状态，并提供缺失分片查询和补发协议。
- 根据限速智能分配带宽：设置页已保存上传/下载限速，`UdpTx` 已有真实发送流，但尚未按配置控制分片发送速度。推荐替代方案：在 UDP 发送器中用令牌桶按目标数分配发送速率。
- 基于传输口令的虚拟小群组传输：当前 UI 没有口令输入，发现协议也没有群组字段。当前无占位入口。推荐替代方案：先在设置或发送页增加口令字段，再把口令哈希放入发现和发送握手协议。
- 基于用户设定状态的条件文件传输：当前已保存用户状态和自定义签名，但发现协议尚未广播状态，发送器也未按状态拦截。当前占位是 `AuthStore.updateStatus(...)` 持久化状态。推荐替代方案：扩展 `LanPeer` 响应字段携带 `UserStatus`，再在真实发送前按 ONLINE/BUSY/INVISIBLE/OFFLINE 做接收策略判断。

## `src/main/java/com/iwmei/lantransfer/App.java`

所属功能：应用启动入口。

详细功能：`App` 只负责把 JVM `main` 方法交给 JavaFX，启动 `MainWindow`。它不保存业务状态，不创建服务对象，也不参与页面路由。

实现方法：`main(String[] args)` 调用 `Application.launch(MainWindow.class, args)`，由 JavaFX 生命周期继续执行 `MainWindow.start(Stage)`。这个文件保持极薄入口，后续不要把登录、网络、传输初始化塞进这里；需要启动时初始化时，优先放到控制器或后端服务里。

## `src/main/java/com/iwmei/lantransfer/controller/AppController.java`

所属功能：界面和业务服务之间的控制层。

详细功能：`AppController` 持有一个 `BackendFacade` 实例，给 JavaFX 页面提供登录、注册、近期设备、全部设备、扫描设备、加载设置、启动传输、更新资料、更新状态和更新设置的统一入口。当前实现实例化 `LocalBackend`，登录注册走本地真实账号仓库，局域网扫描、设置保存和传输报告走本地实现，其余暂未落地功能由本地后端临时复用演示数据。

实现方法：每个公开方法都只做转发，例如 `login(LoginRequest)` 返回 `backend.login(request)` 的 `CompletableFuture<AuthResult>`，`loadSettings()` 返回 `backend.loadSettings()`，`updateProfile(Profile)`、`updateStatus(UserStatus, String)` 和 `updateSettings(SystemSettings)` 直接转给后端。薄控制器保证页面层不直接知道账号文件、设置文件、UDP 传输或扫描实现。

## `src/main/java/com/iwmei/lantransfer/service/BackendFacade.java`

所属功能：前后端交互接口。

详细功能：该接口定义 UI 当前需要的业务能力，包括登录、注册、加载近期传输对象、加载全部设备、扫描局域网设备、加载系统设置、启动传输、更新资料、更新状态和更新设置。它是页面层和真实业务实现之间的边界。

实现方法：查询和长耗时操作返回 `CompletableFuture`，便于 JavaFX 页面在完成后用 `Platform.runLater` 回到 UI 线程；纯写入接口目前是同步 `void`，后续如果写文件或网络操作变慢，应改成 `CompletableFuture<Void>`。`loadSettings()` 是设置页进入时读取 `SystemSettings` 的入口。新增后端功能时先判断 UI 是否真的需要接口，避免提前铺接口。

## `src/main/java/com/iwmei/lantransfer/service/MockBackendFacade.java`

所属功能：前端联调用假数据后端。

详细功能：当前类内置一个 `Profile`、18 个 `UserDevice` 和一份默认 `SystemSettings`，支持演示登录、注册、近期设备、全部设备、扫描设备、设置读取和传输结果。登录只接受 `admin/admin`，注册总是返回“注册申请已提交”，传输总是返回固定的 4 条任务和 5 条日志。

实现方法：所有方法都用 `CompletableFuture.completedFuture(...)` 立即返回，模拟异步后端但不做真实 IO。`loadRecentDevices()` 返回前 5 个设备，`scanLanDevices()` 返回前 4 个设备，`loadSettings()` 返回固定 IP、限速、重试次数和主题配置，`startTransfer(...)` 会在未选择目标时使用前 5 个设备作为兜底目标，并根据目标数量构造 `TransferSummary`。后续真实后端完成后，该类只保留给演示或测试，不再作为主实现。

## `src/main/java/com/iwmei/lantransfer/service/AppFiles.java`

所属功能：本地数据目录工具。

详细功能：统一决定账号文件、设置文件等本地运行数据放在哪里。它把数据放在用户目录 `.lantransfer/<仓库名>/` 下，避免把运行数据写进项目仓库或误提交到 Git。

实现方法：`dataDir()` 使用 `System.getProperty("user.home")`、`.lantransfer` 和 `repoSlug()` 组合路径。`repoOrigin()` 读取 `.git/config` 中的 origin 地址，失败时返回 `LantransferJava`。`repoSlug()` 从 origin URL 取最后一段仓库名，去掉 `.git` 并清理非法路径字符。`AuthStore` 和 `SettingsStore` 都通过它定位文件。

## `src/main/java/com/iwmei/lantransfer/service/AuthStore.java`

所属功能：无服务器登录注册账号仓库。

详细功能：`AuthStore` 负责第一屏登录与注册的真实后端逻辑，也负责“我的”页面资料和状态保存。它通过 `AppFiles` 在用户目录下创建 `.lantransfer/<仓库名>/users.properties`，以当前 GitHub 远程仓库名作为本地账号命名空间，避免把账号数据提交进项目仓库。它支持默认 `admin/admin` 账号、新账号注册、重复账号拦截、账号格式校验、密码 PBKDF2 摘要、登录密码校验、最后登录时间更新、资料更新、状态更新和 `Profile` 构造。

实现方法：`login(LoginRequest)` 先清洗账号并校验空输入，再加载账号文件并确保默认管理员存在；账号不存在时返回失败，密码摘要不匹配时返回失败，匹配时更新 `lastLoginAt`、记录 `currentAccount` 并返回包含资料的 `AuthResult`。`register(RegisterRequest)` 校验账号、密码和重复账号，生成盐和密码摘要，写入用户 ID、昵称、设备名、签名、注册时间、最后登录时间和语言。`updateProfile(Profile)` 通过 `userId` 找账号并保存昵称、设备名、签名和语言。`updateStatus(UserStatus, String)` 使用当前登录账号保存状态枚举和自定义签名；没有登录账号时直接返回。账号文件用 Java `Properties` 读写，密码用 `PBKDF2WithHmacSHA256` 和 120000 次迭代存摘要，不保存明文。该实现是本地替代方案，不依赖服务器和 GitHub token。

## `src/main/java/com/iwmei/lantransfer/service/LocalBackend.java`

所属功能：当前主后端组合实现。

详细功能：`LocalBackend` 是 `AppController` 当前使用的真实后端入口。它把登录、注册、资料保存和状态保存交给 `AuthStore`，把系统设置读取和保存交给 `SettingsStore`，启动 `UdpRx` 后台接收服务，把传输任务创建交给 `UdpTx`，把局域网扫描和已发现设备列表交给 `LanPeer`，把尚未实现的近期对象临时委托给 `MockBackendFacade`，保证 App 在逐步替换后端时仍可运行。

实现方法：构造器调用 `rx.start()`，让应用启动后立即监听 `LanPeer.TRANSFER_PORT`。`login(...)` 和 `register(...)` 使用 `CompletableFuture.supplyAsync(...)` 执行账号文件 IO，避免阻塞 JavaFX 事件线程。`updateProfile(...)` 和 `updateStatus(...)` 同步写入本地账号文件。`loadSettings()` 异步读取 `SettingsStore.load()`，`updateSettings(...)` 写入 `SettingsStore.save(...)`。`loadAllDevices()` 先读取 `LanPeer.knownDevices()`，若当前局域网只知道本机，则回退到演示设备列表，避免课堂展示时用户列表空白。`scanLanDevices()` 异步调用 `LanPeer.scan()`，实际发 UDP 广播并等待同程序响应。`startTransfer(...)` 使用异步任务调用 `UdpTx.run(...)`，传入当前设置中的最大重试次数；如果 UI 未选择目标，则沿用近期传输对象作为兜底目标。后续每完成一个大功能，就把对应方法从 `demo.xxx(...)` 替换为真实实现，并同步更新本文档。

## `src/main/java/com/iwmei/lantransfer/service/SettingsStore.java`

所属功能：系统设置本地仓库。

详细功能：负责读取和保存系统设置页中的 IP、上传/下载限速、最大重试次数、主题色、字体、字号、缩放比例、接收目录、语言和启动选项。它使用 `AppFiles.dataDir()/settings.properties`，不需要数据库。

实现方法：`load()` 先构造默认设置；如果设置文件不存在或读取失败，就直接返回默认值。存在文件时用 `Properties` 读取各字段，整数读取失败时使用默认值，布尔值用 `Boolean.parseBoolean(...)` 解析。`save(SystemSettings)` 把设置写回 properties 文件，并记录 `repo.origin` 方便定位来源。默认 IP 由 `localIp(boolean ipv6)` 遍历启用的非回环网卡获取；找不到时 IPv4 回退 `127.0.0.1`，IPv6 回退 `::1`。默认接收目录来自 `SystemSettings.defaultReceiveDir()`。

## `src/main/java/com/iwmei/lantransfer/service/LanPeer.java`

所属功能：局域网设备发现后端。

详细功能：`LanPeer` 负责实验报告中的“利用广播或组播发现局域网内其他运行本程序的主机”。它维护本机设备信息和已发现设备表，启动后台 UDP 响应线程，扫描时向所有可用广播地址发送发现消息，并把收到的同程序响应转换成 `UserDevice`。发现响应现在携带真实传输 IP 和传输端口，为后续 `UdpTx/UdpRx` 建立目标地址。

实现方法：构造器生成本机 `UserDevice` 并放入 `seen`，默认启动 daemon 响应线程。`scan()` 创建临时 `DatagramSocket`，向 `255.255.255.255` 和所有网卡广播地址发送 `LANTRANSFER_DISCOVER_V1`，在约 900ms 内接收响应并调用 `parse(...)` 写入 `seen`。后台 `replyLoop()` 绑定 `45331` 端口，收到发现消息后用 `encode(self)` 回复发送方；收到设备响应时也会解析并缓存。协议是制表符分隔的短文本：`LANTRANSFER_HERE_V1\t设备ID\t昵称\t设备名\t主机地址\t传输端口`。`parse(message, fallbackHost)` 兼容旧 4 字段响应，缺地址时用 UDP 来源地址兜底，缺端口时用 `45332`。`broadcastAddresses()` 从 `NetworkInterface` 读取可广播地址，`localDevice()` 用系统用户名、主机名、本机 IPv4 和传输端口生成本机条目。该功能不需要服务器；如果防火墙或网段策略拦截 UDP，扫描结果至少保留本机。

## `src/main/java/com/iwmei/lantransfer/service/UdpRx.java`

所属功能：UDP 文件接收后端。

详细功能：`UdpRx` 负责真实文件接收的后台服务。它监听固定传输端口，接收 `UdpTx` 发来的文件开始包和文件内容分片包，为每个文件创建 `.part` 临时文件，按分片序号写入正确偏移量，收齐全部分片后校验 SHA-256，校验通过才移动为最终接收文件。接收目录来自 `SettingsStore.load().receiveDir()`，因此设置页保存的新目录会被后续接收任务使用。

实现方法：`start()` 创建 daemon 线程执行 `listen()`，`listen()` 用可复用地址绑定端口并循环接收 UDP 数据包。协议使用三个短文本头：`LANTRANSFER_FILE_BEGIN_V1` 表示文件开始，携带任务 ID、文件序号、Base64 文件名、文件大小、分片数、分片大小和发送端 SHA-256；`LANTRANSFER_FILE_DATA_V1` 表示文件分片，头部后面直接拼接二进制数据；`LANTRANSFER_FILE_ACK_V1` 是接收端回给发送端的确认。`handleBegin(...)` 创建 `RxFile` 接收状态，`uniqueTarget(...)` 会同时避开已存在文件和本轮正在接收的保留路径，避免并发同名文件互相覆盖。`handleData(...)` 找到对应 `RxFile` 并调用 `write(...)`。`RxFile.write(...)` 使用 `FileChannel` 按 `chunkIndex * chunkSize` 定位写入，`BitSet` 记录哪些分片已经收到，全部收齐后 `finish()` 计算 `.part` 的 SHA-256 并与发送端值比对；校验失败时返回 ACK FAIL，校验成功才把 `.part` 移动成最终文件。当前没有断点续传，相关缺口记录在未实现清单。

## `src/main/java/com/iwmei/lantransfer/service/UdpTx.java`

所属功能：UDP 文件发送后端。

详细功能：`UdpTx` 负责把用户选择的真实文件发送到可达目标设备，并返回传输结果页需要的 `TransferSummary`。它支持普通文件和文件夹展开，按目标设备的 `host/port` 目标级并发发送，在线且可达的设备才会进入真实 UDP 发送；离线或缺少地址的设备会生成失败任务。每个目标内部仍按文件顺序发送：每个文件先计算 SHA-256 并发送开始包，再逐个发送分片包，每个包都等待 `UdpRx` ACK，超时后按系统设置中的最大重试次数重发。

实现方法：`run(...)` 先把 `TransferFile` 展开为 `SourceFile` 列表，文件夹用 `Files.walk(...)` 展开为多个普通文件，`sha256(...)` 用 JDK `MessageDigest` 计算每个源文件校验值，再调用 `sendTargets(...)` 用标准库固定线程池并发处理多个目标，结果仍按原目标顺序汇总。`sendTarget(...)` 为目标创建 `DatagramSocket` 并连接目标地址，随后逐个文件调用 `sendFile(...)`。`sendFile(...)` 发送 `BEGIN` 元数据包，成功后用 `InputStream` 读取固定大小分片并调用 `sendData(...)`。`sendWithAck(...)` 是核心可靠性逻辑：每次发送后等待 ACK，失败则最多重试 `settings.maxRetries()` 次。最终按文件生成 `TransferTask`，按目标汇总成功数、失败数、重试次数、日志和总耗时。当前实现是目标级并发、单目标内停止等待传输；限速、断点续传和实时进度后续在这个类上继续扩展。

## `src/main/java/com/iwmei/lantransfer/service/TxSim.java`

所属功能：文件传输结果报告的本地模拟后端。

详细功能：`TxSim` 是早期在真正 UDP 发送内核完成前使用的本地模拟器，目前主后端已改用 `UdpTx`，该类主要保留给回归自检和必要时的演示兜底。它会读取文件或文件夹大小，按文件和目标组合生成传输列表行，在线目标记为成功，离线目标记为失败并记录三次重试，同时生成开始、每个目标结果和结束日志。

实现方法：`run(List<TransferFile>, List<UserDevice>)` 先把空入参转成空列表，统计总字节数、在线目标数、失败目标数和重试次数，再调用 `tasks(...)` 与 `logs(...)` 构造 `TransferSummary`。`tasks(...)` 对每个文件和每个目标生成一条 `TransferTask`，在线设备进度 100 且状态为“已完成”，离线设备进度 0、速度为 `-`、状态为“传输失败”、重试次数为 3。`sizeOf(Path)` 对文件直接读 `Files.size`，对目录用 `Files.walk` 汇总普通文件大小；异常时按 0 处理，避免 UI 被坏路径中断。后续如果 `UdpTx` 已覆盖所有课堂展示路径，可以删除该类和对应自检。

## `src/main/java/com/iwmei/lantransfer/model/AuthResult.java`

所属功能：认证结果数据对象。

详细功能：记录登录或注册后的结果状态，包括是否成功、是否等待审核、提示消息和返回的用户资料。

实现方法：使用 Java `record` 自动生成构造器和只读访问器。`success` 控制页面是否进入主界面，`pendingReview` 控制注册后是否展示审核等待状态，`message` 用于 toast，`profile` 给 `MainWindow.profile` 保存当前账号资料。

## `src/main/java/com/iwmei/lantransfer/model/LoginRequest.java`

所属功能：登录请求数据对象。

详细功能：封装登录表单中的账号、密码和“记住我”选择。

实现方法：使用 `record` 保持不可变传参。页面层在提交前对账号做 `trim()`，密码原样传给后端校验，`rememberMe` 后续可用于本地保存最近登录账号或令牌。

## `src/main/java/com/iwmei/lantransfer/model/RegisterRequest.java`

所属功能：注册请求数据对象。

详细功能：封装注册表单中的账号、密码和当前设备名称。

实现方法：使用 `record` 作为服务层输入。后续真实注册实现应在服务层校验空账号、弱密码、重复账号和设备名缺省值，并返回 `AuthResult`。

## `src/main/java/com/iwmei/lantransfer/model/Profile.java`

所属功能：当前登录用户资料。

详细功能：保存昵称、用户 ID、设备名称、个性签名、注册时间、最后登录时间、版本信息和语言。文件传输页顶部、我的页面和设置页面都会读取这些信息。

实现方法：使用不可变 `record`，因此修改资料时需要构造新的 `Profile` 并替换 `MainWindow.profile`。时间字段使用 `LocalDateTime`，展示时由 `MainWindow.DATE_TIME` 格式化。

## `src/main/java/com/iwmei/lantransfer/model/SystemSettings.java`

所属功能：系统设置数据对象。

详细功能：描述本机 IPv4/IPv6、上传限速、下载限速、最大重试次数、主题色、字体、字号、缩放比例、接收目录、语言、开机自启动、启动后最小化和传输完成提示音。当前由 `SettingsStore` 读写，并由系统设置页渲染为可编辑控件。

实现方法：使用 `record` 汇总设置页需要保存的数据。保留一个基础字段构造器，旧调用会自动填充默认接收目录、语言和启动选项。`defaultReceiveDir()` 使用用户目录下的 `极速互传/接收文件`。`SettingsStore.load()` 构造或读取它，`Settings` 页面保存时把控件值重新组装成新的 `SystemSettings` 并调用 `AppController.updateSettings(...)`。

## `src/main/java/com/iwmei/lantransfer/model/UserDevice.java`

所属功能：局域网用户设备数据对象。

详细功能：表示一个可传输目标，包含账号或设备 ID、昵称、设备名、在线状态、上次在线时间、头像文字、头像颜色、是否使用图片头像、目标主机地址和目标传输端口。

实现方法：使用 `record` 让设备列表、近期对象、扫描雷达和传输任务共享同一数据结构。保留旧 8 参数构造器，演示数据不用立刻填写地址；`reachable()` 用于判断设备是否有真实传输地址。`DeviceStatus` 决定在线/离线样式，`lastSeen` 当前是展示文本，后续真实在线检测可改为由服务层生成。

## `src/main/java/com/iwmei/lantransfer/model/TransferFile.java`

所属功能：待传输文件数据对象。

详细功能：保存待发送文件或文件夹的显示名称、大小文本和本地路径。

实现方法：文件选择器和拖拽事件创建 `TransferFile`，其中 `Path` 供图标判断、修改时间读取和后续真实传输读取文件内容使用。大小当前是提前格式化好的文本，避免 UI 反复计算。

## `src/main/java/com/iwmei/lantransfer/model/TransferSummary.java`

所属功能：一次传输结果汇总。

详细功能：保存目标总数、成功数、失败数、重试次数、总耗时、日志列表和任务列表。传输结果页用它展示统计卡、传输列表和日志。

实现方法：服务层完成或模拟完成传输后返回该 `record`。`logs` 是按顺序展示的文本，`tasks` 是每个目标或文件的进度行。后续真实 UDP 发送应在最终汇总时把每个目标的确认和重试结果折算进这些字段。

## `src/main/java/com/iwmei/lantransfer/model/TransferTask.java`

所属功能：单个传输任务展示对象。

详细功能：描述传输列表中的一行，包含文件名、目标设备、进度百分比、大小、速度、耗时或完成时间、状态和重试次数。

实现方法：UI 用 `progressPercent` 生成进度条，用 `status` 决定操作按钮和状态徽标。当前状态是中文字符串，后续如逻辑分支增多，可再补枚举；现在保留字符串最省改动。

## `src/main/java/com/iwmei/lantransfer/model/UserStatus.java`

所属功能：用户自定义接收状态枚举。

详细功能：定义默认、在线、忙碌、隐身、离线五种状态。它服务于“我的”页面状态卡和后续条件文件传输逻辑。

实现方法：枚举值只表达状态，不带行为。后续后端根据状态决定是否允许被扫描、是否自动接收、是否弹确认和是否拒绝传输。

## `src/main/java/com/iwmei/lantransfer/model/DeviceStatus.java`

所属功能：设备在线状态枚举。

详细功能：定义设备在列表和传输目标中的在线/离线状态。

实现方法：目前只有 `ONLINE` 和 `OFFLINE`，供 UI 选择颜色、文案和头像强调色。它和 `UserStatus` 不同：`DeviceStatus` 是扫描结果，`UserStatus` 是本机用户设置。

## `src/main/java/com/iwmei/lantransfer/util/FileIcons.java`

所属功能：文件展示工具。

详细功能：负责生成文件修改时间文本、判断文件或文件夹图标、格式化文件大小、识别 Adobe 工程文件、设计文件和常见 IDE 项目文件夹。

实现方法：`modifiedAtLabel(Path)` 读取 `Files.getLastModifiedTime` 并按 `yyyy-MM-dd HH:mm` 展示，异常时返回占位。`iconLiteral(Path)` 先判断目录，目录交给 `folderIcon(Path)` 扫描直接子项，再按扩展名返回 Ikonli 图标字面量。`readableSize(File)` 用字节数换算 MB/KB/B，文件夹直接显示“文件夹”。`folderIcon(Path)` 只扫描一层，这是为拖拽速度保留的简化；如果以后要求深层识别，再改递归扫描。

## `src/main/java/com/iwmei/lantransfer/view/Auth.java`

所属功能：登录与注册页面。

详细功能：显示认证入口、登录表单、注册表单和注册审核等待页。登录成功后写入 `app.profile` 并进入文件传输页，登录失败显示 toast；注册提交后根据后端结果决定显示错误、审核等待页或返回登录页。

实现方法：`show(boolean)` 根据 `registerMode` 选择 `loginForm()` 或 `registerForm()`。`loginForm()` 创建账号、密码、记住我控件，点击登录时构造 `LoginRequest` 调用 `app.controller.login(...)`，异步返回后切回 UI 线程处理结果。`registerForm()` 构造 `RegisterRequest` 调用注册接口；失败时 toast 后端消息，`pendingReview` 为真时进入 `showReviewPending()`，本地注册成功且无需审核时提示“注册成功，请登录”并回到登录页。`showReviewPending()` 保留给以后接入真正审核流程。

## `src/test/java/com/iwmei/lantransfer/service/AuthStoreCheck.java`

所属功能：账号仓库无框架自检。

详细功能：验证第一屏后端最关键路径：默认管理员能登录，新账号能注册，本地注册不进入审核等待，重复注册失败，错误密码失败，正确密码登录成功并返回资料；同时验证资料更新和状态签名更新会持久化。

实现方法：`main(String[] args)` 创建临时目录，把 `AuthStore` 指向临时 `users.properties`，依次调用注册、登录、资料更新、状态更新和再次登录接口，用 `require(...)` 抛出 `AssertionError` 表示失败，最后删除临时目录。运行方式是先编译测试类，再执行 `java -cp target/classes;target/test-classes com.iwmei.lantransfer.service.AuthStoreCheck`。

## `src/test/java/com/iwmei/lantransfer/service/LanPeerCheck.java`

所属功能：局域网发现协议无框架自检。

详细功能：验证 `LanPeer` 的响应文本编码、解析、本机设备兜底、在线状态转换和传输地址携带，不依赖真实网络环境。

实现方法：`main(String[] args)` 使用 `new LanPeer(false)` 禁止启动后台 UDP 线程，构造一个 `UserDevice`，执行 `encode(...)` 和 `parse(...)` 往返检查，再确认解析后的设备 `reachable()` 为真，并确认 `knownDevices()` 至少包含本机设备。失败时 `require(...)` 抛出 `AssertionError`。运行方式是先编译测试类，再执行 `java -cp target/classes;target/test-classes com.iwmei.lantransfer.service.LanPeerCheck`。

## `src/test/java/com/iwmei/lantransfer/service/SettingsStoreCheck.java`

所属功能：系统设置仓库无框架自检。

详细功能：验证默认设置可加载，保存后的上传限速、重试次数、主题色、缩放比例、接收目录、语言和提示音设置能够再次读出。

实现方法：`main(String[] args)` 创建临时 properties 路径，先调用 `load()` 检查默认重试次数，再保存一份自定义 `SystemSettings` 并重新读取，用 `require(...)` 检查关键字段和新字段。最后删除临时文件。运行方式是先编译测试类，再执行 `java -cp target/classes;target/test-classes com.iwmei.lantransfer.service.SettingsStoreCheck`。

## `src/test/java/com/iwmei/lantransfer/service/TxSimCheck.java`

所属功能：传输模拟器无框架自检。

详细功能：验证一个文件发送给一个在线目标和一个离线目标时，汇总统计、失败重试、任务行和日志数量是否正确。

实现方法：`main(String[] args)` 创建临时文件，构造在线和离线两个 `UserDevice`，调用 `new TxSim().run(...)`，用 `require(...)` 检查目标总数为 2、成功为 1、失败为 1、重试为 3、任务行为 2、日志为 4，最后删除临时文件。运行方式是先编译测试类，再执行 `java -cp target/classes;target/test-classes com.iwmei.lantransfer.service.TxSimCheck`。

## `src/test/java/com/iwmei/lantransfer/service/UdpWireCheck.java`

所属功能：UDP 发送接收闭环无框架自检。

详细功能：验证 `UdpTx` 和 `UdpRx` 在本机真实 UDP 环境下可以完成目标级并发发送、ACK 确认、SHA-256 完整性校验和接收落盘。它覆盖最核心的传输闭环：临时接收目录、临时设置文件、临时源文件、两个本机目标设备、发送汇总统计、重名接收文件处理、接收文件内容一致性和错误校验拒收。

实现方法：`main(String[] args)` 创建临时目录和源文件，保存一份带接收目录的 `SystemSettings`，用临时 UDP 端口启动 `UdpRx`，再构造两个 `127.0.0.1` 目标设备并调用 `new UdpTx(1024).run(...)`。检查点包括成功目标数为 2、失败目标数为 0、`hello.txt` 和 `hello-1.txt` 均存在、两个接收文件内容都等于源文件内容。随后 `sendBadChecksumBegin(...)` 手工发送一个 SHA-256 错误的空文件开始包，确认接收端返回 `FAIL` 且不会落盘 `bad.txt`。`freePort()` 用临时 `DatagramSocket(0)` 获取可用端口，`deleteTree(...)` 在结束时清理临时目录。运行方式是先编译测试类，再在 Windows PowerShell 中执行 `java -cp 'target\classes;target\test-classes' com.iwmei.lantransfer.service.UdpWireCheck`。

## `src/main/java/com/iwmei/lantransfer/view/FileTransfer.java`

所属功能：文件传输首页、待发送列表和传输结果页。

详细功能：加载近期传输对象，显示上传文件/文件夹入口，支持拖拽加入待传输项，展示近期目标、传输列表、传输结果统计和传输日志。它也是从登录后进入的第一个主功能页。当前首页传输列表不再固定显示演示任务，而是显示 `app.currentSummary` 中的真实本地传输结果；没有结果时只显示空表头和 0 计数。

实现方法：`showFileTransferPage()` 调用控制器加载近期设备，首次进入时填充 `app.recentTargets`，然后把 `app.currentSummary == null ? List.of() : app.currentSummary.tasks()` 传给 `transferListSection(...)`。`uploadStrip()` 绑定文件选择、文件夹选择、拖拽进入/离开/释放事件，并按 `app.pendingFiles` 动态显示开始发送和清除按钮。`pendingFileCard(...)` 使用 `FileIcons` 展示图标、大小和修改时间。`transferListSection(...)` 根据任务状态动态计算全部、进行中、已完成和已失败数量。`startTransfer()` 校验待传输项，选择 `selectedTargets` 或近期目标作为目标列表，再调用后端返回 `TransferSummary` 并跳转结果页。`showTransferResultPage()`、`resultSummarySection(...)` 和 `transferLogSection(...)` 根据汇总对象展示统计和日志。

## `src/main/java/com/iwmei/lantransfer/view/UserList.java`

所属功能：用户列表页面。

详细功能：展示全部可传输用户，提供搜索框、扫描入口、列表/矩阵视图切换、分页和添加近期传输对象能力。当前设备数据来自 `LocalBackend.loadAllDevices()`：如果局域网已发现其它同程序设备，就显示真实发现结果；如果只发现本机，则显示演示设备列表作为课堂展示兜底。

实现方法：`showUserListPage()` 调用 `loadAllDevices()` 后在 UI 线程组装页面。列表视图逐个调用 `app.userCard(device, true)`，矩阵视图由 `userGrid(...)` 按 15 个一页分页。点击用户卡中的添加按钮会通过 `MainWindow.addRecentTarget(...)` 维护近期和选中目标。

## `src/main/java/com/iwmei/lantransfer/view/Scan.java`

所属功能：局域网扫描页面。

详细功能：显示扫描中状态、雷达图、扫描到的设备标签、隐身用户提示和取消扫描按钮。当前扫描结果来自 `LocalBackend.scanLanDevices()`，后端会实际发 UDP 广播查找同样运行本程序的主机。

实现方法：`showScanPage()` 调用 `scanLanDevices()` 获取设备列表，然后用 `app.radar(devices)` 生成雷达布局。取消按钮回到用户列表。扫描页本身不关心 UDP 细节，只消费 `List<UserDevice>`；广播地址、端口、协议解析和本机响应都在 `LanPeer` 中实现。

## `src/main/java/com/iwmei/lantransfer/view/Mine.java`

所属功能：我的资料页面。

详细功能：展示当前用户资料、状态设置、自定义状态输入和账号更多信息。未登录时会回到登录页。当前“保存”按钮会把现有 `Profile` 写回本地账号文件；自定义状态保存会更新本地账号状态，并把 `app.profile.signature()` 替换成输入文本后重绘页面。

实现方法：`showProfilePage()` 检查 `app.profile`，再组装资料、状态和更多信息三个分区，并给保存/重置按钮绑定动作。保存按钮调用 `app.controller.updateProfile(app.profile)`，重置按钮重新显示页面。`profileEditor(Profile)` 用头像和表格行展示昵称、用户 ID、设备名称和个性签名。`statusCards()` 展示五种 `UserStatus` 对应状态文案。`customStatusField()` 会把当前签名预填到输入框，保存时读取输入框，调用 `app.controller.updateStatus(UserStatus.DEFAULT, text)`，再用 `withSignature(Profile, String)` 构造新的不可变资料对象并刷新页面。

## `src/main/java/com/iwmei/lantransfer/view/Settings.java`

所属功能：系统设置页面。

详细功能：展示本机局域网 IP、上传/下载限速、失败重试次数、主题色、字体、缩放、接收目录、语言和启动设置。当前页面会从后端加载 `SystemSettings`，保存按钮会把可编辑设置写回本地设置文件。

实现方法：`showSettingsPage()` 调用 `app.controller.loadSettings()`，异步返回后在 JavaFX 线程执行 `render(SystemSettings)`。`render(...)` 根据设置值创建各控件，并把上传限速、下载限速、重试次数、主题色、字体、字号、缩放、接收目录、语言和启动选项控件保存到字段，便于保存时读取。`receiveDirControls(...)` 使用 `DirectoryChooser` 选择目录。`colorControls(...)` 点击预设色会用新主题色重绘页面；`saveControls(...)` 读取控件生成新的 `SystemSettings`，调用 `app.controller.updateSettings(...)` 保存，并再次渲染让主题色立即生效。

## `src/main/java/com/iwmei/lantransfer/view/MainWindow.java`

所属功能：JavaFX 主窗口、路由和共享 UI 组件库。

详细功能：该类继承 `Application`，管理窗口尺寸、标题栏、认证窗口壳、主窗口壳、侧边栏、顶部栏、底部状态栏、页面路由、共享状态和大量复用控件。它持有 `AppController`、当前用户资料、当前系统设置、待传输文件、近期目标、选中目标、当前传输汇总、主题色和用户列表分页状态。

实现方法：`start(Stage)` 初始化透明窗口后先异步读取系统设置，拿到 `accentColor` 和 `currentSettings` 后再进入登录页；读取失败时仍进入登录页。`showAuth(...)`、`showFileTransferPage()`、`showUserListPage()`、`showScanPage()`、`showProfilePage()`、`showSettingsPage()` 等方法只做页面路由转发。`setAuthPage(...)` 和 `setMainPage(...)` 把页面内容放入统一窗口壳，`setWindow(...)` 负责 Scene、CSS、尺寸和首次显示。`windowShell(...)`、`mainWindowShell(...)`、`titleBar()`、`sidebar(...)`、`mainTopbar()` 和 `statusFooter()` 组成应用外框；`statusFooter()` 会显示 `currentSettings.receiveDir()`，更改按钮跳转设置页。`cardGrid(...)`、`addCard(...)`、`userCard(...)`、`addRecentTarget(...)`、`tableGrid(...)`、`addTransferRow(...)` 等提供业务页面共享布局。`titleLabel(...)`、`mutedLabel(...)`、`accentLabel(...)`、`textField(...)`、`passwordField(...)`、`primaryButton(...)`、`secondaryButton(...)`、`outlineButton(...)`、`ghostTextButton(...)`、`compactButton(...)`、`iconToggleButton(...)` 和 `textButton(...)` 统一控件样式。`radar(...)`、`scanDeviceLabel(...)`、`statusLine(...)`、`avatar(...)`、`statCard(...)`、`statusCard(...)`、`progressCell(...)`、`operationCell(...)`、`statusBadge(...)` 和 `logLine(...)` 构造具体视觉组件。`copyToClipboard(...)` 和 `toast(...)` 处理用户反馈。
