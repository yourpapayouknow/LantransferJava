# Java 功能说明书

本文档是本项目唯一的 Java 类说明书。每次新增或修改 Java 文件，都要同步更新对应条目；如果功能暂不实现，记录到“功能跳过记录”。

## 当前实现状态

1. 登录与注册：当前主流程已接入 `AuthStore`、仓库根目录 `acco`、`req/` 注册请求和 GitHub Actions 合入机制，登录必须通过账号表校验。
2. 文件传输首页：当前主流程已接入真实近期对象、本地分组目标、文件类型匹配、传输口令、暂停发送、传输进度快照和结果清理。
3. 用户列表：当前主流程已接入局域网发现、本机标识、搜索、扫描按钮内加载动画、新建/取消分组、组编辑、组展开和发送到文件传输页。
4. 局域网扫描：当前主流程使用 UDP 广播、回环地址和可配置发现端口发现同程序客户端，并通过静默广播同步昵称、设备ID、设备名、签名、状态和头像。
5. 我的资料：当前主流程已接入资料保存、头像压缩上传、状态切换、自定义状态、复制用户ID和局域网资料广播。
6. 系统设置：当前主流程已接入本机 IP 展示、上传/下载限速、重试次数、主题色、系统中文字体、缩放、接收目录、启动项和完成提示音；语言字段只保存，不切换界面文案。
7. 传输结果：当前主流程已接入 UDP 多目标发送、BEGIN/DATA/ACK 确认、失败重试、分片并发、SHA-256 校验、分片缓存、断点续传、下载限速、剩余时间日志和结果报告。

## 功能跳过记录

多语言界面本轮按用户要求暂不实现：语言字段继续保存，但界面文案仍使用硬编码中文。后续如重新要求多语言，再新增资源文件并逐页替换文案。

## `src/main/java/com/iwmei/lantransfer/App.java`

所属功能：应用启动入口。

详细功能：`App` 只负责把 JVM `main` 方法交给 JavaFX，启动 `MainWindow`。它不保存业务状态，不创建服务对象，也不参与页面路由。

实现方法：`main(String[] args)` 调用 `Application.launch(MainWindow.class, args)`，由 JavaFX 生命周期继续执行 `MainWindow.start(Stage)`。这个文件保持极薄入口，后续不要把登录、网络、传输初始化塞进这里；需要启动时初始化时，优先放到控制器或后端服务里。

## `src/main/java/com/iwmei/lantransfer/controller/AppController.java`

所属功能：界面和业务服务之间的控制层。

详细功能：`AppController` 持有一个 `BackendFacade` 实例，给 JavaFX 页面提供登录、注册、加载已记住账号、近期设备、全部设备、全部本地分组、保存传输分组、更新传输分组、扫描设备、加载设置、启动传输、带本次口令启动传输、启动传输并接收进度快照、暂停/继续发送、设置接收前确认回调、设置接收进度回调、更新资料、更新状态和更新设置的统一入口。当前实现实例化 `LocalBackend`，登录注册和“记住我”走本地真实账号仓库，用户列表的分组展示、默认口令读取和组名口令编辑走 `GroupStore`，局域网扫描、设置保存、分组展开、暂停发送、传输口令和传输报告走本地实现。状态更新会异步执行，避免“我的”页切换状态时被 `acco` 的 Git 拉取和推送阻塞 JavaFX 事件线程。

实现方法：每个公开方法都只做转发，例如 `login(LoginRequest)` 返回 `backend.login(request)` 的 `CompletableFuture<AuthResult>`，`loadRememberedAccount()` 返回本地最近登录账号，`loadGroups()` 返回 `backend.loadGroups()` 供用户列表页的列表视图展示组卡片，`saveGroup(name, code, members)` 把用户列表当前勾选成员和默认口令交给后端保存并返回组目标，`updateGroup(oldName, name, code, members)` 把组卡片编辑后的新组名和新口令交给后端更新，避免改名时旧组残留，`loadSettings()` 返回 `backend.loadSettings()`，`startTransfer(files, targets, code, progress)` 把页面输入的本次传输口令和进度回调一起交给后端，`pauseTransfer(boolean)` 把暂停状态传给发送服务，`setRxAsk(RxAsk)` 把主窗口的接收确认和口令校验逻辑交给 `UdpRx`，`setRxProgress(RxProgress)` 把主窗口的接收进度展示逻辑交给 `UdpRx`，`updateProfile(Profile)` 和 `updateSettings(SystemSettings)` 直接转给后端。`updateStatus(UserStatus, String)` 返回 `CompletableFuture<Void>`，内部用 `CompletableFuture.runAsync(...)` 调用 `backend.updateStatus(...)`，让状态卡点击后 UI 能马上更新，本地账号表和 Git 同步在后台完成。薄控制器保证页面层不直接知道 `acco` 账号表、设置文件、UDP 传输或扫描实现。

## `src/main/java/com/iwmei/lantransfer/service/BackendFacade.java`

所属功能：前后端交互接口。

详细功能：该接口定义 UI 当前需要的业务能力，包括登录、注册、加载已记住账号、加载近期传输对象、加载全部设备、加载全部本地传输分组、保存本地传输分组、更新本地传输分组、扫描局域网设备、加载系统设置、启动传输、使用本次口令启动传输、启动传输并接收传输中进度快照、暂停或继续当前发送任务、设置接收前确认回调、设置接收进度回调、更新资料、更新状态和更新设置。它是页面层和真实业务实现之间的边界。

实现方法：查询和长耗时操作返回 `CompletableFuture`，便于 JavaFX 页面在完成后用 `Platform.runLater` 回到 UI 线程；纯写入接口目前是同步 `void`，后续如果写文件或网络操作变慢，应改成 `CompletableFuture<Void>`。`loadRememberedAccount()` 只返回账号字符串，不返回密码；`loadGroups()` 返回 `List<Group>`，让用户列表页可以读取组名、默认口令和成员快照；`saveGroup(name, code, members)` 返回一个可放入近期传输对象的组目标；`updateGroup(oldName, name, code, members)` 根据旧组名删除旧记录并写入新记录，供组卡片编辑组名和口令；`loadSettings()` 是设置页进入时读取 `SystemSettings` 的入口。带 `Consumer<TransferSummary>` 的 `startTransfer(...)` 是实时进度入口，带 `String code` 的重载是发送时传输口令入口，所有后端实现都必须显式实现。`pauseTransfer(boolean)` 默认空实现，真实后端会把它交给发送器；`setRxAsk(RxAsk)` 是忙碌状态和口令传输的接收前确认入口，`setRxProgress(RxProgress)` 是接收端进度展示入口，二者默认空实现让临时后端不用处理接收弹窗和进度提示。新增后端功能时先判断 UI 是否真的需要接口，避免提前铺接口。

## `src/main/java/com/iwmei/lantransfer/service/RxAsk.java`

所属功能：接收前确认回调接口。

详细功能：`RxAsk` 是 `UdpRx` 和 JavaFX 主窗口之间的最小确认边界。它表达“某个文件是否允许开始接收”，用于本机状态为 `UserStatus.BUSY` 时询问用户，也用于发送端携带本次传输口令时要求用户输入正确口令。

实现方法：接口使用 `@FunctionalInterface`，只有 `approve(String fileName, long bytes, String codeHash)` 一个方法，返回 true 表示允许接收，返回 false 表示拒收。`codeHash` 为空时表示普通忙碌确认，非空时表示本次传输口令的 SHA-256 摘要。`MainWindow` 传入会弹确认框或口令框的实现，`UdpRx` 在后台线程中调用；调用方可以传入直接 true、false 或摘要比对 lambda 来验证同意、拒绝和口令路径。

## `src/main/java/com/iwmei/lantransfer/service/RxProgress.java`

所属功能：接收进度回调接口。

详细功能：`RxProgress` 是 `UdpRx` 向界面展示接收端进度的最小边界。它只表达文件名和接收百分比，不绑定 `TransferTask`，避免为了接收端提示硬凑发送目标对象。

实现方法：接口使用 `@FunctionalInterface`，只有 `update(String fileName, int percent)` 一个方法。`UdpRx.RxFile` 在分片写入后按 25% 阈值和最终 100% 调用它，`MainWindow` 传入 toast 展示实现，调用方可传入收集字符串的 lambda 验证 100% 回调。

## `src/main/java/com/iwmei/lantransfer/service/AppFiles.java`

所属功能：本地数据目录工具。

详细功能：统一决定设置、近期对象、本地登录状态等运行数据放在哪里。默认把数据放在用户目录 `.lantransfer/<仓库名>/` 下，避免把运行状态误提交到 Git；多实例运行时，可以通过 `lantransfer.dataDir` JVM 参数或 `LANTRANSFER_DATA_DIR` 环境变量为每个运行端指定独立数据目录，隔离设置、近期对象、接收目录和“记住我”状态。账号主凭据不放在这里，而是放在根目录 `acco` 并由 GitHub Actions 处理注册请求。

实现方法：`dataDir()` 先调用 `configuredDataDir()` 读取 `System.getProperty("lantransfer.dataDir")`，如果 JVM 参数为空再读取 `System.getenv("LANTRANSFER_DATA_DIR")`；只要覆盖值非空，就直接把它转成 `Path` 返回。没有覆盖值时，才使用 `System.getProperty("user.home")`、`.lantransfer` 和 `repoSlug()` 组合默认路径。`repoOrigin()` 读取 `.git/config` 中的 origin 地址，失败时返回 `LantransferJava`。`repoSlug()` 从 origin URL 取最后一段仓库名，去掉 `.git` 并清理非法路径字符。`AuthStore` 只通过它定位本机 `la` 登录状态，`SettingsStore` 和 `RecentStore` 通过它定位设置与近期对象；多端运行时必须给不同端传入不同目录，避免两个 JavaFX 实例共享本地运行状态。

## `src/main/java/com/iwmei/lantransfer/service/AutoStart.java`

所属功能：Windows 开机自启动管理。

详细功能：`AutoStart` 负责把系统设置里的“开机自启动”落到 Windows 当前用户启动目录。启用时创建 `极速互传.cmd`，关闭时删除该脚本；生产默认构造只在 Windows 系统上生效，非 Windows 系统不做副作用。它不修改注册表，不需要管理员权限，适合课程项目和本机运行。

实现方法：默认构造器通过 `%APPDATA%/Microsoft/Windows/Start Menu/Programs/Startup` 定位启动目录，`sync(boolean)` 根据开关创建目录、写入脚本或删除脚本。脚本先切到当前项目根目录，再优先运行 `mvnw.cmd -q javafx:run`，没有 Maven Wrapper 时运行 `mvn -q javafx:run`，并用 `start /min` 尽量最小化启动命令窗口。`AutoStart(Path)` 允许把启动脚本写到指定目录，`none()` 表示不执行系统启动项写入。

## `src/main/java/com/iwmei/lantransfer/service/AuthStore.java`

所属功能：无服务器登录注册账号仓库。

详细功能：`AuthStore` 负责第一屏登录与注册的真实后端逻辑，也负责“我的”页面资料、状态保存和“记住我”账号回填。账号主凭据来自仓库根目录短名账号表 `acco`，注册时本地程序生成 `req/<账号>` 请求文件并直接 push 到当前 GitHub 远程仓库，`.github/workflows/acco.yml` 的 GitHub Actions 会自动把请求合入 `acco` 并删除请求文件，相当于自动审核通过。push 只能使用由微信开通 VIP 后获取的注册授权 Token：`tok.ps1` 首次把该 Token 加密保存到当前 Windows 用户目录，`run.ps1` 启动时解密并只给当前 App 进程注入 `ACCO_T` 作为 VIP 授权 Token；没有 Token 或 Token 格式不正确时注册明确失败并提示联系微信客服，不退回本机 Git 凭据，避免误用用户主 GitHub 账号。账号表保存账号、盐、PBKDF2 密码摘要、用户 ID、昵称、设备名、签名、注册时间、最近登录时间、语言、状态、审核字段 and 压缩后的头像 Base64；不保存明文密码。`la` 只保存本机“记住我”状态，不再作为主凭据库。辅助账号邮箱密码不能作为 GitHub API 或 Git push 凭据写入代码，token 也不能提交到仓库。

实现方法：`login(LoginRequest)` 先用 `pullAccounts()` 执行 `git fetch origin <当前分支>`，再从 `FETCH_HEAD` 检出仓库账号表 `acco`，避免登录同步进入 `rebase` 状态；随后用 `readAccounts()` 读取 CSV 表头之后的账号行；账号不存在返回失败，密码摘要不匹配返回失败，匹配时只在内存中更新 `lastLoginAt` 用于本次 `Profile`，并把“记住我”写入本机 `la`。`register(RegisterRequest)` 校验账号、密码和重复账号后，用 `putAccount(...)` 生成盐、PBKDF2 摘要、默认资料字段和空头像字段，用 `approveRegistration(...)` 写入 `reviewStatus=AUTO_APPROVED` 与 `reviewApprover=actions`；启用 Git 同步时调用 `saveReq(...)` 写入 `req/<账号>`，再用 `pushPath(...)` 只暂存该请求文件、调用 `ensureGitIdentity()` 补齐最小 Git 提交身份、提交短消息 `acco req` 并推送。`push(...)` 会先调用 `pushUrl()` 根据 `AppFiles.repoOrigin()` 提取 `owner/repo`，如果存在 `-Dacco.t` 或 `ACCO_T`，就构造一次性 HTTPS token 地址执行 `git push <临时地址> HEAD:<当前分支>`；如果没有 token，就返回“未检测到 VIP 授权 Token，请联系微信客服开通 VIP 获取注册权限”，不调用普通 `origin` 推送。`clean(...)` 会在错误输出中遮盖 token 或 URL 编码后的 token，避免失败信息把密钥显示给界面。推送后 `waitForAction(...)` 每 5 秒同步一次远程账号表，最多等待 45 秒；如果 Actions 已把账号合入 `acco`，返回注册成功并提示登录，否则返回 `pendingReview=true` 进入等待页。指定账号表构造器会关闭 Git 同步，只读写传入 of `acco`。`profile(...)` 从账号行构造 `Profile` 时读取 `avatar` 字段，没有头像时为空字符串；`updateProfile(Profile)` 通过 `userId` 找到账号行，同时保存昵称、设备名、签名、语言、状态和头像，再直接提交推送 `acco`；`updateStatus(UserStatus, String)` 只更新状态与签名。如果 Git 暂存区已有其它改动，`pushPath(...)` 会拒绝操作，避免把无关文件带入账号提交。

## `src/main/java/com/iwmei/lantransfer/service/LocalBackend.java`

所属功能：当前主后端组合实现。

详细功能：`LocalBackend` 是 `AppController` 当前使用的真实后端入口。它把登录、注册、记住账号、资料保存和状态保存交给 `AuthStore`，把系统设置读取和保存交给 `SettingsStore`，把近期传输对象读取和保存交给 `RecentStore`，把本地传输分组详情读取、分组保存、分组更新和分组目标展开交给 `GroupStore`，启动 `UdpRx` 后台接收服务，把接收前确认回调、接收进度回调和本机用户状态同步给 `UdpRx`，把传输任务创建、本次口令、暂停/继续和发送端进度快照交给 `UdpTx`，把局域网扫描和已发现设备列表交给 `LanPeer`，并在登录、资料修改和状态切换后刷新本机发现信息。资料刷新会把昵称、设备名、签名、头像和按账号加传输端口生成的设备ID交给局域网发现模块静默广播。传输请求通过单线程后台队列执行，避免用户连续点击或页面重复提交时多个传输任务同时竞争 UDP 发送和近期对象写入。

实现方法：构造器先读取设置中的 `groupCode` 并调用 `lan.updateGroup(...)` 保留旧发现协议字段兼容，再调用 `rx.start()`，让应用启动后立即监听 `LanPeer.TRANSFER_PORT`。`login(...)`、`register(...)` 和 `loadRememberedAccount()` 使用 `CompletableFuture.supplyAsync(...)` 执行账号表拉取、注册请求推送和本地 `la` 读取，避免阻塞 JavaFX 事件线程；登录成功后调用 `lan.updateSelf(profile)` 和 `rx.updateStatus(profile.status())`，让发现协议和接收门禁同时使用当前账号状态。`setRxAsk(RxAsk)` 调用 `rx.setAsk(...)` 保存主窗口确认和口令校验回调，`setRxProgress(RxProgress)` 调用 `rx.setProgress(...)` 保存主窗口接收进度回调。`updateProfile(...)` 通过 `AuthStore` 更新并推送 `acco` 后刷新 `LanPeer` 本机资料，如果资料不为空也把状态同步给 `UdpRx`；`updateStatus(...)` 保存状态后同时调用 `lan.updateStatus(status, customText)` 和 `rx.updateStatus(...)`，让 ONLINE/BUSY/INVISIBLE/OFFLINE 与自定义签名同时参与扫描、发送策略和接收门禁。`loadSettings()` 异步读取 `SettingsStore.load()`，`updateSettings(...)` 写入 `SettingsStore.save(...)` 后再次调用 `lan.updateGroup(...)`，但当前发现层不再按旧全局口令隔离设备。`loadRecentDevices()` 只返回 `GroupStore.targets()` 与 `RecentStore.load()` 中未重复的真实本地对象；为空时交给前端空态和发送前校验处理。`loadGroups()` 异步返回 `GroupStore.all()`，给用户列表页列表视图使用；`saveGroup(name, code, members)` 保存组名、默认口令和勾选成员快照并返回带默认口令的组目标，`updateGroup(oldName, name, code, members)` 调用 `GroupStore.update(...)` 删除旧组名并写入新组名和新口令。`loadAllDevices()` 直接读取 `LanPeer.knownDevices()`，没有发现其它设备时只返回本机或空结果，不再补默认用户。`scanLanDevices()` 异步调用 `LanPeer.scan()`，实际发 UDP 广播并等待同程序响应。`transferQueue` 使用 JDK `Executors.newSingleThreadExecutor(...)` 创建 daemon FIFO 队列；普通 `startTransfer(...)` 把口令设为空字符串，带 `code` 的 `startTransfer(...)` 排队执行时先把空目标转成空列表，再用 `GroupStore.expand(...)` 把组目标展开成真实成员，随后调用 `UdpTx.run(files, safeTargets, settings, code, progress)`，最后用用户原始请求目标调用 `RecentStore.remember(...)` 保存近期对象。`pauseTransfer(boolean)` 直接调用 `UdpTx.setPaused(...)`，影响当前发送器后续 UDP 包发送。

## `src/main/java/com/iwmei/lantransfer/service/SettingsStore.java`

所属功能：系统设置本地仓库。

详细功能：负责读取和保存系统设置页中的 IP、上传/下载限速、最大重试次数、主题色、字体、字号、缩放比例、接收目录、传输口令、语言和启动选项。它使用 `AppFiles.dataDir()/settings.properties`，不需要数据库；保存时会把“开机自启动”同步给 `AutoStart`，让设置项具备系统级效果。

实现方法：`load()` 先构造默认设置；如果设置文件不存在或读取失败，就直接返回默认值。存在文件时用 `Properties` 读取各字段，整数读取失败时使用默认值，布尔值用 `Boolean.parseBoolean(...)` 解析，传输口令读取 `groupCode`，缺失时回退空字符串表示公开组。`save(SystemSettings)` 把设置写回 properties 文件，记录 `repo.origin` 方便定位来源，然后调用 `autoStart.sync(value.autoStart())` 创建或删除启动目录脚本。默认构造器使用真实 `AutoStart`，指定构造器可使用 `AutoStart.none()` 避免系统启动项写入。默认 IP 由 `localIp(boolean ipv6)` 遍历启用的非回环网卡获取；找不到时 IPv4 回退 `127.0.0.1`，IPv6 回退 `::1`。默认接收目录来自 `SystemSettings.defaultReceiveDir()`，启动后最小化默认关闭，避免首次登录成功后窗口被自动隐藏到托盘而像程序崩溃。

## `src/main/java/com/iwmei/lantransfer/service/RecentStore.java`

所属功能：近期传输对象本地仓库。

详细功能：`RecentStore` 负责把最近传输过的目标设备保存到本地 `recent.properties`，让文件传输首页重启后仍能显示真实近期对象，而不是只依赖本次进程内存。它保存 `UserDevice` 的 ID、昵称、设备名、在线状态、用户状态、最近传输时间、头像首字、头像颜色、图片头像标记、头像 Base64、个性签名、目标地址和传输端口，最多保留 12 个。

实现方法：默认构造器使用 `AppFiles.dataDir().resolve("recent.properties")` 定位文件。`load()` 用 `Properties` 读取 `count` 和每个序号下的设备字段，字段缺失时使用安全默认值，设备在线状态解析失败时回退 `DeviceStatus.OFFLINE`，用户状态解析失败时回退 `UserStatus.DEFAULT`。`remember(List<UserDevice>)` 把本次目标更新时间后放到 `LinkedHashMap` 前面，再追加旧记录并按 ID 去重，最后截取前 12 个写回。`save(...)` 创建父目录并写入 properties，同时记录 `repo.origin`；`put(...)` 保存 `userStatus`、`signature` 和 `avatar`，让近期对象重启后仍保留对方通过 UDP 发来的资料。`touched(...)` 只更新时间文本，保留用户状态、签名和头像。该实现用本地文件替代数据库，足够满足无服务器课堂项目的近期对象恢复。

## `src/main/java/com/iwmei/lantransfer/service/GroupStore.java`

所属功能：本地传输分组仓库。

详细功能：`GroupStore` 负责用户列表中的新建分组、编辑分组、分组列表展示和文件传输页中的组目标展开。它把分组名、默认口令和组内用户快照保存到 `groups.properties`，读取时可以返回完整 `Group` 详情，也可以生成 `UserDevice.group(...)` 形式的组目标；编辑组名时会删除旧组名记录并写入新组名记录，避免列表里出现重复组。当文件传输页选择组作为近期传输对象时，后端会把该组展开成组内所有真实成员，再交给 `UdpTx` 非阻塞并发发送。分组保存的是用户当时的网络地址、端口、状态、签名和头像信息，不依赖服务器。

实现方法：默认构造器使用 `AppFiles.dataDir().resolve("groups.properties")` 定位文件；指定文件构造器允许传入分组文件。`save(name, code, members)` 先用 `UserDevice.cleanGroupName(...)` 清洗组名，再用 `cleanMembers(...)` 去掉空值、组目标和重复成员，成员为空时抛出异常；写入成功后返回 `UserDevice.group(groupName, memberCount)` 供调用方知道分组目标已经创建，实际带默认口令的传输对象由后续 `targets()` 或 `Group.target()` 重新从文件中读取生成。`update(oldName, name, code, members)` 使用同一套成员清洗逻辑，先从 `loadGroups()` 结果中移除 `oldName` 清洗后的旧 key，再写入新 `Group`，用于组卡片编辑组名和默认口令。`all()` 读取所有分组并返回 `Group` 详情，用户列表页用它显示组名、`共xx名用户`、默认口令和展开成员；`targets()` 从 `Group.target()` 得到所有组目标，目标的 `signature` 字段会携带默认口令。`expand(targets)` 遍历用户选择的目标，普通用户原样保留，组目标按 `groupName()` 查表展开，使用 `LinkedHashMap` 按 ID 去重，避免同一用户既被单独选中又在组里时重复发送。`loadGroups()` 和 `writeGroups(...)` 使用 Java `Properties` 保存 `count`、组名、`code`、成员数量和每个成员字段；`put(...)` 和 `device(...)` 会写入并读取 `signature` 与 `avatar`，让分组成员在稍后发送时仍带有用户资料快照；设备状态和用户状态解析失败时分别回退 `OFFLINE` 和 `DEFAULT`。

## `src/main/java/com/iwmei/lantransfer/service/LanPeer.java`

所属功能：局域网设备发现后端。

详细功能：`LanPeer` 负责实验报告中的“利用广播或组播发现局域网内其他运行本程序的主机”。它维护本机设备信息、已发现设备表、最后发现时间和旧协议分组摘要兼容字段，启动后台 UDP 响应线程，扫描时向所有可用广播地址和本机回环地址发送发现消息，并把收到的同程序响应转换成 `UserDevice`。发现请求和响应仍可携带旧口令 SHA-256 摘要，但局域网发现不再按该字段隔离设备，避免设置页移除全局传输口令后旧本地配置残留导致两台同网段机器互相不可见；真正的传输口令只在发送文件时由 `UdpTx/UdpRx` 做接收前校验。发现响应还携带真实传输 IP、传输端口、`UserStatus`、个性签名和压缩头像，为 `UdpTx/UdpRx` 建立目标地址并提供状态条件传输依据，也让用户列表能静默收到对方资料。设备ID按账号、主机地址和传输端口生成，同账号在本机多实例或多台机器登录时也不会在发现缓存中互相覆盖。发现端口默认是 `49131`，传输端口默认是 `49132`；多实例运行时，发现端口可通过 `lantransfer.discoveryPort` / `LANTRANSFER_DISCOVERY_PORT` 覆盖，扫描端口集合可通过 `lantransfer.discoveryPorts` / `LANTRANSFER_DISCOVERY_PORTS` 配置，传输端口可通过 `lantransfer.transferPort` / `LANTRANSFER_TRANSFER_PORT` 覆盖，让不同运行端既能避开本机端口冲突，又能互相扫描到对方。默认端口从旧的 `45331/45332` 调整到 `49131/49132`，原因是当前 Windows 环境会对旧端口段返回 `Address already in use`，导致发现线程和接收线程启动后立即退出。已发现设备超过离线阈值未再次出现时，会在用户列表中标记为离线。当前用户切到隐身或离线状态时，本机不响应发现请求。

实现方法：构造器生成本机 `UserDevice` 并通过 `remember(...)` 放入 `seen` 和 `seenAt`，默认启动 daemon 响应线程；默认离线阈值为 30 秒，指定构造器可传入自定义阈值。静态字段 `PORT` 由 `configuredPort("lantransfer.discoveryPort", "LANTRANSFER_DISCOVERY_PORT", 49131)` 初始化，静态字段 `TRANSFER_PORT` 由 `configuredPort("lantransfer.transferPort", "LANTRANSFER_TRANSFER_PORT", 49132)` 初始化；`configuredPort(...)` 先读 JVM 参数，再读环境变量，解析为正整数就使用配置值，解析失败或未配置时回退默认端口。`SCAN_PORTS` 由 `configuredScanPorts()` 初始化，始终包含当前发现端口，并额外解析 `lantransfer.discoveryPorts` 或 `LANTRANSFER_DISCOVERY_PORTS` 中用逗号、分号或空白分隔的端口列表。`PACKET_BYTES` 提到 60000 字节，给 128px JPEG 头像 Base64 留出空间。`updateGroup(String)` 只把旧全局口令清洗后计算 SHA-256 十六进制摘要并保存在 `groupHash`，用于继续编码兼容旧发现消息，不再作为发现准入条件。`scan()` 创建临时 `DatagramSocket`，向 `127.0.0.1`、`255.255.255.255` 和所有网卡广播地址发送 `discoverMessage()`，并且对 `SCAN_PORTS` 中每个端口都发一次；实际发送由 `sendDiscover(...)` 执行，单个地址或端口发送失败只跳过当前目标，不会阻断其他地址，也不会跳过后续接收阶段；发现消息可以是 `LANTRANSFER_DISCOVER_V1` 或 `LANTRANSFER_DISCOVER_V1\t旧口令摘要`，二者在当前版本都会被接收。后台 `replyLoop()` 绑定当前 `PORT`，收到发现消息后只用 `groupMatches(...)` 通过旧字段兼容检查并继续检查 `discoverable(self.userStatus())`，`groupMatches(...)` 固定返回 true，所以旧口令不同不会再阻止回复；隐身和离线仍不回复；如果发现端口绑定失败，会直接打印异常，方便从多实例窗口看到端口冲突。允许发现时用 `encode(self)` 回复发送方，收到设备响应或资料广播时也会解析并缓存。响应协议是制表符分隔的短文本：`LANTRANSFER_HERE_V1\t设备ID\t昵称\t设备名\t主机地址\t传输端口\t用户状态\t旧口令摘要\t个性签名\t头像Base64`；`parse(message, fallbackHost)` 仍兼容旧 4 到 8 字段响应，缺状态时回退 `UserStatus.DEFAULT`，缺签名和头像时为空，旧口令摘要存在、缺失或不同都会被接收。`avatar(String)` 会限制头像字段长度并只接受 Base64 字符，避免异常网络包污染 UI。`updateSelf(Profile)` 在登录或资料修改后先用账号、当前主机地址和当前传输端口调用 `idFor(...)` 生成设备ID，再用账号资料刷新本机发现身份并调用 `announce()`；这能让同一账号在 8 个本机实例中得到 8 个不同设备ID，避免 `seen` 表按 ID 覆盖。`updateStatus(UserStatus, String)` 在状态切换后刷新本机状态和签名并再次静默广播；`announce()` 直接把 `encode(self)` 发到所有广播地址和扫描端口，对端后台线程会在没有手动扫描的情况下接收并更新缓存。`knownDevices()` 通过 `sorted()` 返回设备时会调用 `withStatus(...)`，按最后发现时间和用户状态生成 ONLINE/OFFLINE 及“刚刚/秒前/分钟/对方隐身/对方离线/已离线”展示文本，并保留对方签名和头像。`broadcastAddresses()` 会加入回环地址和全局广播地址，再从 `NetworkInterface` 读取可广播地址；回环地址用于没有 VM 权限时的本机双实例运行，真实局域网仍依赖广播地址。`localDevice()` 用系统用户名、主机名和当前传输端口生成未登录本机条目。该功能不需要服务器；如果防火墙或网段策略拦截 UDP，扫描结果至少保留本机。

## `src/main/java/com/iwmei/lantransfer/service/UdpRx.java`

所属功能：UDP 文件接收后端。

详细功能：`UdpRx` 负责真实文件接收的后台服务。它默认监听 `LanPeer.TRANSFER_PORT`，接收 `UdpTx` 发来的文件开始包和文件内容分片包；该端口默认是 `49132`，可由 `lantransfer.transferPort` JVM 参数或 `LANTRANSFER_TRANSFER_PORT` 环境变量覆盖，方便多实例运行。监听线程只负责收包和处理 BEGIN，DATA 分片交给 8 个接收 worker 并行处理，接收 socket 会扩大缓冲区，避免稍大文件的多线程分片把系统 UDP 缓冲挤爆后出现“第xx分片未确认”。每个接收文件会创建 `.part` 临时文件和 `.part.meta` 分片状态文件，按分片序号写入正确偏移量，并按接收进度节点调用 `RxProgress` 供界面展示；如果接收端重启后再次收到同一文件的开始包，会从 `.part.meta` 恢复已接收分片并在 BEGIN ACK 中带回缺失分片列表，供发送端定向补发。接收端会用 `FileIcons.supportedName(...)` 拒收不支持的文件类型；本机状态为 BUSY 或发送端携带本次传输口令摘要时，`UdpRx` 会在创建接收状态前调用 `RxAsk` 询问用户是否允许接收，有口令时按口令摘要缓存确认结果，无口令忙碌确认按发送任务ID缓存，同一批文件或同一目录压缩包只弹一次确认或口令框；本机状态为 INVISIBLE 或 OFFLINE 时直接拒收。DATA 包写入成功后，`UdpRx` 会按 BEGIN 时读取到的 `downloadLimit` 延迟 ACK，形成接收侧下载限速闭环；下载限速不会在每个分片重新读取 `settings.properties`，否则大文件会被磁盘配置读取拖慢。收齐全部分片后校验 SHA-256，校验通过才移动为最终接收文件；如果 BEGIN 扩展字段标记为 `DIRZIP`，说明发送端传的是文件夹临时压缩包，接收端会把 zip 解压到同名目录并删除 zip 文件，从而保留子目录结构。最终删除元数据并推送 100% 接收进度。接收目录来自 `SettingsStore.load().receiveDir()`，因此设置页保存的新目录会被后续接收任务使用。

实现方法：`start()` 创建 daemon 线程执行 `listen()`，`listen()` 用可复用地址绑定端口、扩大接收缓冲并循环接收 UDP 数据包；如果监听线程因端口占用、权限或网络栈异常退出，会把异常打印到进程错误日志，方便 GUI 多端运行时定位失败原因。`updateStatus(UserStatus)` 保存当前本机状态，`setAsk(RxAsk)` 保存接收前确认回调，空回调会自动允许；`setProgress(RxProgress)` 保存接收进度回调，空回调为空操作。协议使用三个短文本头：`LANTRANSFER_FILE_BEGIN_V1` 表示文件开始，携带任务 ID、文件序号、Base64 文件名、文件大小、分片数、分片大小、发送端 SHA-256、本次传输口令 SHA-256 摘要和文件夹压缩包标记；`LANTRANSFER_FILE_DATA_V1` 表示文件分片，头部后面直接拼接二进制数据；`LANTRANSFER_FILE_ACK_V1` 是接收端回给发送端的确认。`handle(...)` 收到 BEGIN 时同步调用 `handleBegin(...)`，收到 DATA 时用 `workers.execute(...)` 交给接收 worker。`handleBegin(...)` 创建 `RxFile` 接收状态，如果同一任务文件已经在本轮接收中，就直接用已有 `RxFile.missing()` 生成缺失分片扩展字段并 ACK；新文件会先解析文件名、大小、口令摘要和 `DIRZIP` 标记，不支持的扩展名直接返回 `ACK FAIL` 和 `UNSUPPORTED`，通过类型检查后调用 `allowBegin(jobId, fileName, size, codeHash)`，DEFAULT/ONLINE 且无口令时直接允许，INVISIBLE/OFFLINE 直接拒绝，BUSY 或有口令时用 `approvalKey(jobId, codeHash)` 读取 `approvals` 缓存，`approvalKey(...)` 在口令摘要非空时直接使用口令摘要，在口令为空时使用 jobId，缓存没有记录时才调用一次 `ask.approve(fileName, size, codeHash)`，后续 BEGIN 包直接复用第一次同意或拒绝结果；同意后才通过 `createFile(...)` 和 `restoreOrReset()` 读取或创建 `.part`，拒绝时返回 `ACK FAIL` 和 `REJECTED` 扩展字段。`createFile(...)` 会把当前 `RxProgress`、BEGIN 时读取到的下载限速字节数和 `folderZip` 标记传入 `RxFile`，避免 DATA 阶段每个分片都读取设置文件。`handleData(...)` 找到对应 `RxFile` 并调用 `write(...)`，写入成功后用本文件缓存的下载限速调用 `downloadRate.pause(...)`，通过推迟 ACK 控制发送端速度；0 表示不限速。`uniqueTarget(...)` 会同时避开已存在文件和本轮正在接收的保留路径，避免并发同名文件互相覆盖。`RxFile` 初始化时读取 `.part.meta`，如果 size、chunkCount、chunkSize 和 SHA-256 匹配，就恢复已接收的 `BitSet`；如果不匹配或没有元数据，就清理旧 `.part`，随后打开一次 `.part` 的 `FileChannel` 供后续分片复用。`RxFile.write(...)` 对同一个文件使用同步入口，避免最终移动文件时和重复分片写入赛跑；实际写入复用同一个 `FileChannel` 并按 `chunkIndex * chunkSize` 定位，`BitSet` 记录哪些分片已经收到；新分片只在首片、每 16 片和最终完成时保存 `.part.meta`，保留断点续传能力并避免大文件每片刷元数据拖慢 ACK。未收齐时 `publishProgress()` 根据 `receivedCount * 100 / chunkCount` 按 25% 阈值调用 `progress.update(fileName, percent)`，重复分片不会重复推送；全部收齐后先 `finish()` 关闭临时文件通道、计算 SHA-256 并移动最终文件，`folderZip` 为 true 时调用 `unzipTarget()` 用 JDK `ZipInputStream` 解压到同名目录。`unzipDir(...)` 遇到已有目录会生成 `目录名-1` 这样的新目录，`unzipEntry(...)` 会检查解压后的标准化路径仍在目标目录内，防止异常 zip 条目越界写文件；解压完成后删除接收到的 zip。成功后再 `publish(100)`，校验或解压失败则不推送完成进度。`missing()` 只在已经存在部分分片时返回缺失索引，例如只收到第 0 片且总共 2 片时返回 `1`；完全没有历史分片时返回空字符串，让发送端按普通新任务完整发送。`RateLimit.pause(...)` 低于 1ms 的亚毫秒等待直接累计跳过，避免 Windows 把短睡眠放大成十几毫秒。

## `src/main/java/com/iwmei/lantransfer/service/UdpTx.java`

所属功能：UDP 文件发送后端。

详细功能：`UdpTx` 负责把用户选择的真实文件发送到可达目标设备，并返回传输结果页需要的 `TransferSummary`。它支持普通文件和文件夹发送：普通文件仍按 `FileIcons` 白名单判断，文件夹会在发送前用 JDK `ZipOutputStream` 打成临时 zip，作为一个文件发送，接收端再解压回同名目录，因此不会把目录内部文件打散到接收目录根部。发送时可接收文件传输页传入的本次口令，计算 SHA-256 摘要后随 BEGIN 包发送给接收端。它按“目标×文件”组合并发发送：如果选择 4 个用户和 5 个文件，会创建 20 个文件目标发送任务，同时覆盖老师要求的多目标并发和多文件并发。每个文件目标任务都使用 UDP socket 直接向对应主机发送 BEGIN 和 DATA；同一目标的多个文件共享一个 jobId 和目标级 `RateLimit`，保证本次口令只确认一次，上传限速也不会因为多文件并发被放大。在线、可达且用户状态为 DEFAULT/ONLINE/BUSY 的设备会进入真实 UDP 发送，离线、缺少地址、隐身和离线状态的设备会生成失败任务和拦截日志。BUSY 目标不会被发送端直接拦截，而是发送 BEGIN 后等待接收端确认。每个文件先计算 SHA-256 并发送开始包，如果接收端 BEGIN ACK 返回缺失分片列表，就只补发这些缺失分片，没有缺失列表时按普通新任务发送全部分片。DATA 阶段在只有 1 个文件目标任务时使用最多 8 个分片 worker 展示单文件分片并发；一旦外层已经存在多个“目标×文件”并发任务，单文件内部就固定为 1 个 worker，避免多对象发送时把 UDP 包和 ACK 数量叠到接收端缓冲无法承受。每个分片 ACK 等待 2 秒，超时后按系统设置中的最大重试次数重发；发送端会在正式提交并发 Future 前先通过进度回调推送整批“传输中”初始任务，让传输列表立即出现，随后多分片文件在 25%/50%/75% 进度点记录预计剩余时间日志，并通过进度回调推送当前文件目标的状态快照。发送过程中支持暂停和继续：暂停只阻塞后续 UDP 包发送，不破坏已经收到 ACK 的分片状态。

实现方法：两参数 `run(...)` 调用带口令参数的 `run(...)` 并传入空口令，带 `Consumer<TransferSummary>` 的旧入口同样进入同一发送路径。`setPaused(boolean)` 使用 `pauseLock` 保存暂停状态，继续时 `notifyAll()` 唤醒等待线程；`waitIfPaused()` 在每次 `sendWithAck(...)` 真正发送 UDP 包前执行，暂停期间阻塞，线程被中断时返回失败并保留中断标记。带口令的 `run(...)` 先用 `sources(...)` 把 `TransferFile` 转成真正要发送的 `SourceFile` 列表，再用 `codeHash(code)` 把本次口令转成 SHA-256 摘要；普通文件只有 `FileIcons.supported(path)` 为 true 才进入列表，文件夹调用 `zipDir(...)` 创建临时 zip。`zipDir(...)` 用 `Files.createTempFile("ltx", ".zip")` 生成临时包，用 `Files.walk(root)` 遍历目录，并用 `zipEntry(...)` 把普通文件和空目录写入 zip 条目，条目名用相对路径并把反斜杠改成 `/`；生成的 `SourceFile` 文件名为原文件夹名加 `.zip`，`folderZip=true`，`temporary=true`。`zipName(...)` 负责给无扩展目录名补 `.zip`，`deleteTemps(...)` 在 `run(...)` 的 finally 中删除临时包，避免发送结束后污染系统临时目录。`sha256(...)` 用 JDK `MessageDigest` 计算源文件或临时 zip 的校验值。过滤或打包后如果没有任何可传输源文件，直接返回失败汇总并写入“没有可传输文件”日志，避免空路径误报成功。`sendableTargets(...)` 统计真实可发送目标，`perTargetBytesPerSecond(...)` 把 `SystemSettings.uploadLimit()` 按可发送目标数量折算为每目标字节速率；当可发送目标数和文件数的乘积大于 1 时，日志写入“文件目标并发：N 个任务”。`publishStart(...)` 在提交真正发送 Future 之前，为所有可发送目标和所有源文件生成 0% 且状态为“传输中”的 `TransferTask`，并通过回调交给页面，这就是提交后传输列表能立即出现的来源。`sendTargets(...)` 的写法对齐旧作业 `EchoServerUDP` 收到 UDP 包后立即启动处理线程的思路：它先为每个目标创建 `TargetJob`，统计本次真实 `activeTasks`，再把每个可发送目标和每个源文件组合成 `FileFuture`，用固定线程池一次性提交所有文件目标任务，因此 4 个用户和 5 个文件会同时提交 20 个任务；离线、隐身、不可达目标不会提交任务，只在汇总阶段生成失败行。每个 `sendFileTask(...)` 都创建独立 `DatagramSocket`、连接目标地址并调用 `sendFile(...)`，避免多个文件共享同一个 socket 时 ACK 互相抢；同一目标的多个任务共享 `TargetJob.jobId()` 和 `TargetJob.rate()`，所以口令确认和上传限速仍按目标聚合。`finishJobs(...)` 等待全部 Future 后按原目标顺序、原文件顺序重建 `TargetSend`，再由 `run(...)` 汇总成功目标数、失败目标数、任务列表、日志和重试次数。`sendFile(...)` 发送 `BEGIN` 元数据包，`beginMessage(...)` 写入任务 ID、文件序号、Base64 文件名、文件大小、分片数、分片大小、文件 SHA-256、本次口令 SHA-256 和 `DIRZIP/FILE` 标记；`beginTimeout(...)` 对所有 BEGIN ACK 统一使用 15 秒等待，因为 BEGIN 是唯一可能触发接收端确认弹窗或口令框的阶段。DATA 分片使用 2 秒 ACK 等待，覆盖接收端多线程写盘和局域网调度抖动。`sendWithAck(...)` 会临时切换 socket 超时、发送包、等待 ACK、失败时按最大重试次数重发，并在 finally 中恢复旧超时；它还会解析 ACK 第 6 个字段并放入 `AckResult.detail()`。`chunksToSend(...)` 把该字段中的逗号分隔缺失分片索引转成待发送列表，字段为空或解析失败时发送全部分片。`sendChunks(...)` 是单文件分片调度入口，调用 `chunkWorkers(chunks.size(), activeTasks)` 决定 worker 数；`chunkWorkers(...)` 在 `activeTasks > 1` 时直接返回 1，让多对象传输只保留外层目标文件并发，避免每个任务再叠加 8 个分片 worker 导致 UDP 包爆发和“第xx分片未确认”。只有 `activeTasks == 1` 且分片数大于 1 时，才按分片数、CPU 数和 `MAX_CHUNK_WORKERS` 创建最多 8 个 worker，并写入“分片并发：N 个 worker”日志。`sendChunks(...)` 用 `AtomicInteger cursor` 分配分片索引、`AtomicInteger failedChunk` 记录首个失败分片、`AtomicInteger totalRetries` 汇总重试次数、`AtomicLong sentBytes` 汇总已确认字节。`sendWorker(...)` 为每个 worker 创建独立 `DatagramSocket`，连接同一目标地址和端口，在循环中领取分片、调用 `sendChunk(...)`、等待该 worker socket 收到对应 ACK；不同 worker 的 ACK 不会互相抢。`sendChunk(...)` 用 `FileChannel` 和 `readChunk(...)` 按 `chunkIndex * chunkBytes` 位置读取数据，`readChunk(...)` 使用 `FileChannel.read(buffer, position)` 做并发安全的定位读取，不修改共享 channel 的当前位置。续传时仍可跳过已接收分片，只补缺失分片，并在日志中记录“断点续传：仅补发 N 个缺失分片”。单个文件开始发送后 `publishProgress(...)` 继续推送当前文件目标的 0% 快照；每个分片 ACK 后累计已发送字节，`publishChunkProgress(...)` 在 25%/50%/75% 阈值处写日志、计算 ETA、推送包含当前文件、目标、百分比、速度、耗时和日志副本的快照。`RateLimit.pause(...)` 使用同步方法保护目标级限速计数，避免多个分片 worker 同时修改发送字节，低于 1ms 的亚毫秒等待会直接累计跳过，避免 Windows 短睡眠拖慢大文件。最终按文件生成 `TransferTask`，按目标汇总成功数、失败数、重试次数、日志和总耗时。

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

详细功能：封装注册表单中的账号、密码和当前设备名称。当前真实注册由 `AuthStore.register(...)` 使用它生成 `req/<账号>` 注册请求，并等待 GitHub Actions 合入 `acco`。

实现方法：使用 `record` 作为服务层输入，页面只负责传入表单值。空账号、弱密码、重复账号和设备名缺省值都在 `AuthStore` 内校验或清洗，避免界面层和后端重复实现规则；注册结果统一通过 `AuthResult` 返回给 `Auth.registerForm()`。

## `src/main/java/com/iwmei/lantransfer/model/Profile.java`

所属功能：当前登录用户资料。

详细功能：保存昵称、用户 ID、设备名称、个性签名、注册时间、最后登录时间、版本信息、语言、当前用户状态和头像 Base64。文件传输页顶部、我的页面、用户列表发现身份和设置页面都会读取这些信息。

实现方法：使用不可变 `record`，因此修改资料、状态或头像时需要构造新的 `Profile` 并替换 `MainWindow.profile`。组件末尾追加 `avatar` 字段，保留旧 8 参数和 9 参数构造器，旧调用会自动使用 `UserStatus.DEFAULT` 和空头像。时间字段使用 `LocalDateTime`，展示时由 `MainWindow.DATE_TIME` 格式化；头像字段由 `Mine` 压缩成 128px JPEG 后写入，登录时由 `AuthStore.profile(...)` 从 `acco` 读取。

## `src/main/java/com/iwmei/lantransfer/model/SystemSettings.java`

所属功能：系统设置数据对象。

详细功能：描述本机 IPv4/IPv6、上传限速、下载限速、最大重试次数、主题色、字体、字号、缩放比例、接收目录、传输口令、语言、开机自启动、启动后最小化和传输完成提示音。当前由 `SettingsStore` 读写；系统设置页只编辑 IP 展示、限速、重试、主题、字体、缩放、接收目录、语言和启动项，传输口令字段暂保留给旧配置兼容。

实现方法：使用 `record` 汇总设置页需要保存的数据。保留一个基础字段构造器，旧调用会自动填充默认接收目录、空传输口令、语言和启动选项，其中开机自启动和启动后最小化都默认关闭，完成提示音默认开启。`defaultReceiveDir()` 使用用户目录下的 `极速互传/接收文件`。`SettingsStore.load()` 构造或读取它，`Settings` 页面保存时把控件值重新组装成新的 `SystemSettings` 并调用 `AppController.updateSettings(...)`。

## `src/main/java/com/iwmei/lantransfer/model/Group.java`

所属功能：本地传输分组数据对象。

详细功能：保存用户列表页新建分组后的完整展示和传输信息，包括分组名、默认口令和成员快照。列表视图用 `name` 作为卡片标题，用 `size()` 展示“共xx名用户”，用 `code` 展示默认口令；文件传输页不直接发送 `Group`，而是通过 `target()` 转成带默认口令的 `UserDevice.group(...)` 组目标，再由 `GroupStore.expand(...)` 展开成真实成员发送。

实现方法：使用 Java `record` 保持不可变。紧凑构造器会调用 `UserDevice.cleanGroupName(name)` 统一处理空组名，把空口令转为空字符串，并用 `List.copyOf(...)` 固定成员列表，避免页面或后端保存后再被外部集合修改。`size()` 直接返回 `members.size()`，`target()` 使用当前组名、成员数量和默认口令构造近期传输对象需要的组目标，后续文件传输页会把该口令预填到“本次传输口令”输入框。该类只承载本地分组数据，不做文件 IO，读写逻辑仍放在 `GroupStore`。

## `src/main/java/com/iwmei/lantransfer/model/UserDevice.java`

所属功能：局域网用户设备数据对象。

详细功能：表示一个可传输目标，包含账号或设备 ID、昵称、设备名、设备在线状态、用户状态、上次在线时间、头像文字、头像颜色、是否使用图片头像、目标主机地址、目标传输端口、个性签名和头像 Base64。它也可以表示本地传输分组目标：分组目标的 ID 使用 `GROUP:` 前缀，显示为“组：分组名”，实际发送前由 `GroupStore` 展开成组内真实用户；分组目标的 `signature` 字段保存默认口令，只给发送前口令输入框预填使用，不作为普通用户签名展示。

实现方法：使用 `record` 让设备列表、近期对象、用户列表卡片和传输任务共享同一数据结构。保留旧 8 参数构造器、带地址构造器和带用户状态构造器，旧调用会自动使用 `UserStatus.DEFAULT`、空签名和空头像；新增的 `signature` 与 `avatar` 放在末尾，降低对已有调用点的影响。`reachable()` 用于判断设备是否有真实传输地址。`groupTarget()` 通过 `GROUP:` 前缀判断当前对象是否为分组，`groupName()` 返回前缀后的分组名，`group(String, int)` 创建无默认口令的分组卡片，`group(String, int, String)` 创建带默认口令的分组卡片，组目标自身不携带头像。`cleanGroupName(String)` 统一清理空组名。`DeviceStatus` 决定在线/离线样式，`UserStatus` 决定对方是否允许被发现或直接接收文件，`lastSeen` 是由 `LanPeer`、`RecentStore` 或 `GroupStore` 生成的展示文本。

## `src/main/java/com/iwmei/lantransfer/model/TransferFile.java`

所属功能：待传输文件数据对象。

详细功能：保存待发送文件或文件夹的显示名称、大小文本和本地路径。

实现方法：文件选择器和拖拽事件创建 `TransferFile`，其中 `Path` 供图标判断、修改时间读取和 `UdpTx` 读取真实文件内容使用。大小是提前格式化好的文本，避免 UI 反复计算。

## `src/main/java/com/iwmei/lantransfer/model/TransferSummary.java`

所属功能：一次传输结果汇总。

详细功能：保存目标总数、成功数、失败数、重试次数、总耗时、日志列表和任务列表。`UdpTx` 在真实发送开始、分片进度和最终完成时都会构造它，文件传输页用它展示统计卡、传输列表和日志，也用它执行“清除已完成”“移除单条任务”和“清空日志”的内存汇总更新。

实现方法：服务层完成真实传输时返回该 `record`。`logs` 是按顺序展示的文本，`tasks` 是每个目标或文件的进度行。`withoutCompleted()` 过滤掉状态为“已完成”的任务，并用剩余任务重新计算目标数、成功数、失败数和重试数；`withoutTask(TransferTask)` 移除传入任务并复用同一套 `with(...)` 统计重算逻辑，供传输列表“移除”按钮使用；`withoutLogs()` 保留统计和任务，只把日志列表换成空列表。`UdpTx` 最终汇总会把每个目标的确认结果、失败结果和重试次数折算进这些字段，进度快照则用同一结构表达当前“传输中”任务。

## `src/main/java/com/iwmei/lantransfer/model/TransferTask.java`

所属功能：单个传输任务展示对象。

详细功能：描述传输列表中的一行，包含文件名、目标设备、进度百分比、大小、速度、耗时或完成时间、状态和重试次数。

实现方法：UI 用 `progressPercent` 生成进度条，用 `status` 决定操作按钮和状态徽标。当前状态是中文字符串，后续如逻辑分支增多，可再补枚举；现在保留字符串最省改动。

## `src/main/java/com/iwmei/lantransfer/model/UserStatus.java`

所属功能：用户自定义接收状态枚举。

详细功能：定义默认、在线、忙碌、隐身、离线五种状态。它服务于“我的”页面状态卡、局域网发现响应、用户列表展示、发送端条件过滤和接收端门禁。

实现方法：枚举值只表达状态，不带行为，具体判断分散在最靠近业务的位置：`LanPeer.discoverable(...)` 让隐身和离线用户不响应发现；`UdpTx.sendable(...)` 让隐身和离线目标不进入发送；`UdpRx.allowBegin(...)` 让忙碌状态弹确认、隐身和离线状态拒收、默认和在线状态自动接收无口令请求。`AuthStore` 把该枚举名保存到 `acco`，`Profile` 和 `UserDevice` 负责携带当前值。

## `src/main/java/com/iwmei/lantransfer/model/DeviceStatus.java`

所属功能：设备在线状态枚举。

详细功能：定义设备在列表和传输目标中的在线/离线状态。

实现方法：目前只有 `ONLINE` 和 `OFFLINE`，供 UI 选择颜色、文案和头像强调色。它和 `UserStatus` 不同：`DeviceStatus` 是扫描结果，`UserStatus` 是本机用户设置。

## `src/main/java/com/iwmei/lantransfer/util/FileIcons.java`

所属功能：文件展示工具。

详细功能：负责生成文件修改时间文本、判断文件或文件夹图标、格式化文件大小、识别文件类型标签、判断当前是否允许传输该类型、识别 Adobe 工程文件、设计文件和常见 IDE 项目文件夹。

实现方法：`modifiedAtLabel(Path)` 读取 `Files.getLastModifiedTime` 并按 `yyyy-MM-dd HH:mm` 展示，异常时返回 `修改日期：-`。`iconLiteral(Path)` 先判断目录，目录交给 `folderIcon(Path)` 扫描直接子项，再按扩展名返回 Ikonli 图标字面量。`SUPPORTED` 保存当前允许传输的扩展名白名单；`supported(Path)` 允许文件夹和白名单文件，`supportedName(String)` 给接收端用文件名做同样校验，无扩展名按普通文件允许。`typeLabel(Path)` 返回“图片、文档、表格、代码、未知类型”等短标签，文件传输页用它展示匹配结果。`readableSize(File)` 用字节数换算 MB/KB/B，文件夹直接显示“文件夹”。`folderIcon(Path)` 只扫描一层，这是为了拖拽时快速显示图标；真正发送文件夹时由 `UdpTx` 先压缩成 zip，接收端再解压恢复目录结构。

## `src/main/java/com/iwmei/lantransfer/util/DeviceSearch.java`

所属功能：用户列表搜索匹配工具。

详细功能：`DeviceSearch` 负责按搜索框输入匹配 `UserDevice` 的昵称、设备名称、设备 ID、目标主机地址和个性签名。它不依赖 JavaFX，因此页面能复用同一套匹配逻辑。

实现方法：`matches(UserDevice, String)` 对空搜索词直接返回 true；非空搜索词用 `Locale.ROOT` 转小写并 `trim()`，再调用 `contains(...)` 分别检查昵称、设备名、ID、host 和 `signature`。`contains(...)` 对 null 字段返回 false，避免局域网设备缺少地址或签名时触发异常。

## `src/main/java/com/iwmei/lantransfer/view/Auth.java`

所属功能：登录与注册页面。

详细功能：显示认证入口、登录表单、注册表单和 GitHub Actions 等待提示页。登录表单会异步读取本地已记住账号并回填账号框，密码框不会从本地文件回填；登录和注册密码栏都有小眼睛按钮，可以在隐藏密码和明文显示之间切换。登录成功后写入 `app.profile` 并进入文件传输页，登录失败显示 toast；注册按钮显示为“注册”，提交后会禁用表单并显示加载动画，后端返回后再根据结果决定显示错误、等待页或返回登录页。等待页目前复用“注册审核提示”视觉文案，实际触发条件是 `AuthStore.waitForAction(...)` 在等待远程 Actions 合入账号表时还没有看到新账号。

实现方法：`show(boolean)` 根据 `registerMode` 选择 `loginForm()` 或 `registerForm()`。`passwordBox(String)` 用一个 `PasswordField` 和一个绑定同一文本的 `TextField` 叠放在 `StackPane` 中，默认只显示 `PasswordField`；点击 `mdi2e-eye` 图标按钮时切换两个输入框的 `visible/managed` 状态，并把图标切到 `mdi2e-eye-off`，因此同一份密码值不会复制到多个业务变量。`labeledPassword(...)` 复用页面标签样式包装密码组合控件。`loginForm()` 创建账号、密码、记住我控件，不预置任何账号；随后调用 `app.controller.loadRememberedAccount()`，如果有已保存账号，则在 JavaFX 线程回填账号、清空密码并勾选“记住我”。点击登录时从 `PasswordBox.getText()` 取密码构造 `LoginRequest` 调用 `app.controller.login(...)`，异步返回后切回 UI 线程处理结果；成功时保存后端返回的 `Profile` 并调用 `app.showFileTransferPage()`，失败时只 toast 后端消息。`registerForm()` 把“注册”按钮和 `ProgressIndicator` 放在同一个 `StackPane` 里，按钮始终 `setMaxWidth(Double.MAX_VALUE)` 满宽，加载圈用 `StackPane.setAlignment(..., Pos.CENTER_RIGHT)` 浮在右侧，不参与挤压按钮宽度；点击提交后立即显示加载圈、禁用账号/密码/设备/提交按钮，然后构造 `RegisterRequest` 调用注册接口；返回后隐藏加载动画并恢复表单，失败时 toast 后端消息，`pendingReview` 为真时保存临时 `Profile` 并进入 `showReviewPending()`，注册成功且无需等待时 toast 后端消息并回到登录页。`showReviewPending()` 构建一个主窗口壳里的提示页，返回、我知道了和返回登录按钮都会回到登录页；它不直接轮询 Actions，再次登录会由 `AuthStore.login(...)` 重新拉取最新 `acco`。

## `src/main/java/com/iwmei/lantransfer/view/FileTransfer.java`

所属功能：文件传输首页、待发送列表和传输结果页。

详细功能：加载传输对象，显示上传文件/文件夹入口，支持拖拽加入待传输项，展示传输对象、传输列表、传输结果统计、传输日志和发送端传输中进度快照。选择或拖拽文件时会用 `FileIcons.supported(...)` 做文件类型匹配，不支持的扩展名会被跳过并 toast 提示；待传输卡片会显示类型标签、大小和修改时间。传输对象可以是单个用户，也可以是本地分组；选择分组发送时后端会展开组内成员并对所有成员并发发送，组目标携带的默认口令会预填到发送前口令输入框。传输对象区域标题显示为“传输对象”，对象卡片按每行 5 个自动换行，不限制对象总数。它也是从登录后进入的第一个主功能页。当前首页传输列表显示 `app.currentSummary` 中的真实本地传输结果；没有结果时只显示空表头和 0 计数。传输开始前会询问“本次传输口令”，空值表示无口令；传输开始后页面会先进入结果页显示空汇总，后端初始进度快照到达后立即显示整批“传输中”任务，后续多个发送线程推来的单任务快照会按“文件名+目标”合并更新，不会把其它任务行覆盖掉，最终 Future 完成后播放提示音并刷新为完整结果。传输过程中会显示“暂停发送/继续发送”按钮。传输结果页的“清除已完成”“重试”“移除”“清空日志”和“自动滚动”复选框会直接更新当前内存状态并刷新页面。

实现方法：`showFileTransferPage()` 调用控制器加载近期设备，首次进入时把返回对象全部加入 `app.recentTargets`，然后把 `app.currentSummary == null ? List.of() : app.currentSummary.tasks()` 传给 `transferListSection(...)`。`uploadStrip()` 绑定文件选择、文件夹选择、拖拽进入/离开/释放事件，并按 `app.pendingFiles` 动态显示开始发送和清除按钮；没有待传输项时提示文字为“或拖拽到此处”，发送中会禁用开始和清除按钮，并通过 `pauseButton()` 显示当前暂停状态。`addFiles(...)` 逐个调用 `FileIcons.supported(...)`，支持的文件加入 `pendingFiles`，不支持的计数后提示“已跳过不支持的文件类型：N个”；文件夹允许加入，真正发送时由 `UdpTx` 压缩成临时 zip 并由接收端解压。`pendingFileCard(...)` 使用 `FileIcons.iconLiteral(...)`、`FileIcons.typeLabel(...)`、`FileIcons.readableSize(...)` 和 `FileIcons.modifiedAtLabel(...)` 展示图标、类型、大小和修改时间。`recentTargetsSection(...)` 使用 `app.glassSection("传输对象")` 创建传输对象区，用 `app.cardGrid(5, 8, 8)` 和 `app.addCard(..., i, 5)` 按 5 列排布全部对象，行数随对象数量自然增长。`transferListSection(...)` 根据任务状态动态计算全部、进行中、已完成和已失败数量；已完成数量为 0 或没有汇总时禁用“清除已完成”；每一行调用 `app.addTransferRow(...)` 时传入 `retryTask(task)` 和 `removeTask(task)`，让“重试/移除”按钮真正执行动作。`clearCompletedTasks()` 调用 `TransferSummary.withoutCompleted()` 后重绘结果页，`removeTask(...)` 调用 `TransferSummary.withoutTask(task)` 后重绘结果页，`clearLogs()` 调用 `TransferSummary.withoutLogs()` 后重绘结果页。`retryTask(...)` 把失败任务目标写入 `app.selectedTargets`，再复用当前仍在待传输区的文件或文件夹调用 `startTransfer()` 重新发送；如果待传输项已经被清空，则只提示“原待传输项已清空”。`transferLogSection(...)` 用 `CheckBox` 展示“自动滚动”，勾选状态保存在 `app.autoScrollLogs`，刷新日志区域时如果已勾选就把 `ScrollPane` 滚到最底部。`startTransfer()` 校验待传输项和目标列表，`hasUsableTarget(...)` 要求至少有一个真实可达用户或组目标，未选择有效对象时直接提示用户选择；随后选择 `selectedTargets` 或近期目标作为目标列表，调用 `askTransferCode(...)` 弹出本次口令输入框并通过 `app.styleDialog(dialog)` 套用深色弹窗样式，`defaultGroupCode(...)` 从组目标的 `signature` 读取默认口令预填，取消则不发送。确认后重置暂停状态，写入空 `TransferSummary` 并进入结果页，再调用 `app.controller.startTransfer(files, targets, code, progress)`；进度回调和最终完成回调都通过 `Platform.runLater(...)` 调用 `showTransferProgress(...)`，最终完成时调用 `app.playDoneSound()`，最终完成或异常时清理 `transferRunning/transferPaused`。`showTransferProgress(...)` 不再直接覆盖 `app.currentSummary`，而是调用 `mergeSummary(...)` 合并旧汇总和新快照；`mergeSummary(...)` 用 `LinkedHashMap` 按 `taskKey(...)` 去重，`taskKey(...)` 由文件名、目标ID、目标host和端口组成，保证并发线程发来的单条任务只替换自己的行；`mergeLogs(...)` 用同样的顺序 Map 合并日志，避免多个快照反复刷新时日志丢失。`togglePause()` 翻转 `app.transferPaused` 并调用 `app.controller.pauseTransfer(...)`。`showTransferResultPage()`、`resultSummarySection(...)` 和 `transferLogSection(...)` 根据汇总对象展示统计和日志。

## `src/main/java/com/iwmei/lantransfer/view/UserList.java`

所属功能：用户列表页面。

详细功能：展示局域网用户和本地传输分组，并把两个视图明确拆开：矩阵视图只展示单一用户，列表视图只展示分组。当前设备数据来自 `LocalBackend.loadAllDevices()`，分组数据来自 `LocalBackend.loadGroups()`；用户卡片第一行显示“设备名称 | 昵称”，如果该设备是当前登录本机，会在设备名称前加 `(本机)`。矩阵视图搜索框提示为“搜索用户昵称或设备名称”，只按昵称和设备名称过滤。列表视图搜索框提示为“搜索分组名或口令”，分组卡片标题放大显示组名，副标题大小保持不变，口令存在时显示“共xx名用户 | 口令：xxx”，口令为空时只显示“共xx名用户”；分组卡片提供发送、编辑和展开/收起按钮，编辑按钮会切换为强调色对勾，允许直接修改组名和默认口令，展开后在卡片内部按三列矩阵展示组内用户卡片。顶部保留搜索、扫描用户和新建分组入口，删除旧的“上次扫描刚刚”、分组文本框和“选中建组”按钮；扫描用户不再进入独立扫描页，而是在按钮文字右侧显示转圈动画并留在当前用户列表页；进入新建分组状态后会出现取消按钮，避免误点后只能完成建组。

实现方法：`showUserListPage()` 用 `thenCombine(...)` 同时加载用户设备和分组详情，完成后切回 JavaFX 线程调用 `renderPage(...)`。`renderPage(...)` 根据 `app.userListGridView` 设置搜索提示词，搜索框变化或搜索图标按钮点击时更新 `query`、重置页码并调用 `renderResults(...)`。扫描按钮点击 `scanUsers()`，该方法递增 `scanRunId`、设置 `scanning=true` 并重绘当前页；重绘时按钮文本改为“扫描中”，`app.smallSpinner()` 作为按钮 graphic 放在文字右侧，按钮禁用，避免转圈动画压住文字；随后调用 `app.controller.scanLanDevices()`，完成后清除扫描状态、toast 提示并刷新列表。`enterGrouping()` 进入新建分组状态，会清空草稿、清空组名和默认口令、强制切到矩阵视图；此时单一用户卡片通过 `app.userCard(device, true, true)` 隐藏发送按钮，并在卡片右侧叠加复选框，勾选状态保存到 `groupDraft`。建组状态下，“新建分组”按钮变成强调色“确认分组”，旁边显示“取消”按钮，再向右显示“请输入分组名”和“请输入默认口令，无则留空”两个输入框；`cancelGrouping()` 清空草稿并退出建组状态，`saveSelectedGroup(name, code)` 要求组名非空、至少勾选一个用户，然后调用 `app.controller.saveGroup(groupName, groupCode, members)` 保存分组，成功后退出建组状态并切到列表视图展示新分组。`renderResults(...)` 在矩阵视图中过滤并分页用户，在列表视图中过滤并渲染分组；`groupCard(...)` 使用现有大用户卡片样式构建无头像组卡片，标题用 `app.titleLabel(group.name(), 22)` 放大，副标题先生成“共xx名用户”，再在 `group.code()` 非空时拼接“ | 口令：xxx”，发送按钮调用 `app.addRecentTarget(group.target())` 后跳转文件传输页，编辑按钮初始为铅笔图标，点击后把组名和口令展示切成两个输入框，并把按钮换成强调色对勾；再次点击调用 `saveGroupEdit(...)`，由 `app.controller.updateGroup(...)` 保存新组名和新口令，保存成功后保留展开状态并重绘页面。展开按钮把组名加入或移出 `expandedGroups` 后重绘页面。`memberGrid(...)` 用三列 `GridPane` 在组卡片内部展示成员卡片；`userGrid(...)` 保持 15 个用户一页的分页逻辑。

## `src/main/java/com/iwmei/lantransfer/view/Mine.java`

所属功能：我的资料页面。

详细功能：展示当前用户资料、状态设置、自定义状态输入和账号更多信息。未登录时会回到登录页。我的资料卡片左侧展示可修改头像，头像尺寸放大到 136px，编辑区内边距统一为 16px，让头像左侧边距与上下边距一致；鼠标悬停头像时叠加半透明灰色圆形遮罩和“修改”文字，点击后选择本地图片并保存为压缩头像。右侧展示昵称、用户ID、设备名称和个性签名四项资料；右侧资料表单固定为原先宽度的约80%，整体靠卡片右侧排列，资料标签在表单内右对齐。用户ID行是只读展示，右侧使用复制图标按钮写入系统剪贴板。昵称、设备名称和个性签名行默认只读，右侧使用编辑图标按钮进入编辑状态；进入编辑状态后按钮会变成强调色对勾图标，点击对勾会保存当前资料、退出编辑状态并提示保存成功。底部“保存”仍可一次性保存当前表单值，底部“重置”会按当前账号资料重新渲染页面。状态卡可以直接点击切换，当前状态会高亮；自定义状态保存会更新账号状态，并把 `app.profile.signature()` 替换成输入文本后重绘页面。

实现方法：`showProfilePage()` 检查 `app.profile`，把 `Profile.status()` 写入 `selectedStatus`，再组装资料、状态和更多信息三个分区，并给保存/重置按钮绑定动作。`profileEditor(Profile)` 用 `HBox` 放头像、弹性空白和资料表单，`root.setMaxWidth(Double.MAX_VALUE)` 让容器吃满资料卡片宽度，`root.setPadding(new Insets(16))` 统一头像所在区域边距，弹性空白把资料表单推到右侧，`PROFILE_FORM_WIDTH` 和三列约束把表单宽度收窄到约542px。`profilePhoto(Profile)` 调用 `app.avatar(..., profile.avatar())` 显示图片头像或首字头像，并叠加 `.profile-avatar-mask` 遮罩；点击头像触发 `chooseAvatar()`。`chooseAvatar()` 使用 JavaFX `FileChooser` 只选择常见图片扩展名，选中后调用 `jpegAvatar(File)`。`jpegAvatar(...)` 用 JDK `ImageIO` 读取图片，按中心正方形裁剪，下采样绘制到 128×128 的 `BufferedImage.TYPE_INT_RGB`，用 JPEG writer 按 0.72 压缩质量写入内存，再 Base64 编码成账号表和 UDP 协议可传输文本；处理失败时只提示“头像处理失败”。头像保存时用 `withAvatar(readProfile(), data)` 保留当前表单文字，调用 `app.controller.updateProfile(...)` 写回账号表并触发后端广播。`editableRow(...)` 创建默认只读的 `TextField` 和图标按钮，按钮初始为 `mdi2p-pencil` 编辑图标，点击后切换输入框可编辑状态、把按钮样式从 `compact-button` 改为 `primary-button` 并把图标改成 `mdi2c-check`；再次点击时调用 `readProfile()` 读取昵称、设备名称、个性签名和头像，调用 `app.controller.updateProfile(...)` 写回账号表，然后恢复编辑图标。`profileRow(...)` 专门构建用户ID只读行，使用 `mdi2c-content-copy` 图标按钮调用 `app.copyToClipboard(...)`。`addProfileLabel(...)` 统一创建右对齐资料标签，`profileIconButton(...)` 和 `profileIcon(...)` 统一创建资料卡片的小图标按钮。底部保存按钮调用 `readProfile()` 从输入框构造新的 `Profile`，再调用 `app.controller.updateProfile(...)` 持久化。`statusCards()` 展示五种 `UserStatus` 对应状态文案，`statusCard(...)` 绑定点击事件并调用 `saveStatus(...)`；`saveStatus(...)` 调用异步 `app.controller.updateStatus(...)` 后立即更新 `app.profile` 并刷新页面，不等待 `AuthStore` 内部的 `git pull/push` 完成，避免状态切换期间整个进程卡顿。`customStatusField()` 会把当前签名预填到输入框，保存时复用当前 `selectedStatus`。

## `src/main/java/com/iwmei/lantransfer/view/Settings.java`

所属功能：系统设置页面。

详细功能：展示本机局域网IP、上传/下载限速、失败重试次数、主题色、字体、缩放、接收目录、语言和启动设置。当前页面会从后端加载 `SystemSettings`，保存按钮会把可编辑设置写回本地设置文件；原先放在设置页的一刀切传输口令已去除，字体、字号和缩放会更新 `MainWindow.currentSettings` 并在页面重绘时应用到全局根节点样式。

实现方法：`showSettingsPage()` 调用 `app.controller.loadSettings()`，异步返回后在 JavaFX 线程执行 `render(SystemSettings)`。`render(...)` 先更新 `app.currentSettings` 和 `app.accentColor`，再根据设置值创建各控件，并把上传限速、下载限速、重试次数、主题色、字体、字号、缩放、接收目录、语言和启动选项控件保存到字段，便于保存时读取。页面根容器使用 `Pos.TOP_CENTER`，设置列表使用无框 `VBox` 并铺满页面可用宽度，边界留白由 `.page-content` 统一控制，避免外层卡片套叠和过窄列表；每个设置项由 `settingsRow(...)` 构建为四列 `GridPane`，左列固定 210 宽放标题和副标题，中列固定分隔线，第三列为弹性空白，右列固定 740 宽放控件，让标题尽量贴左并把控件组推向右侧且不越界，标题列和控件列都垂直居中，避免控件随内容长度漂移。`uiRow(...)` 统一把控件行设置为 `Pos.CENTER_RIGHT`，让标签、输入框、色块、选择框和复选框在各自行内垂直对齐并贴向右侧控件列。每个设置项副标题由 `settingsRow(...)` 控制，空副标题不会创建说明文字，当前副标题均保持短句且不带标点；本机局域网IP、失败重试次数、界面颜色自定义和接收目录不再显示副标题，传输速度限制显示“不限速请设定为0”。`ipInfo(...)` 使用`IPv4地址`和`IPv6地址`文案，避免中英文之间额外空格。`speedLimitControls(...)` 使用一行两列展示“上传限制”和“下载限制”；`colorControls(...)` 使用一行展示五个预设色块、自定义色块、自定义输入框，点击色块会用新主题色重绘页面；`fontControls(...)` 不再展示字体预览，只用 `fontBox(...)` 从 JavaFX 系统字体族中过滤常见中文字体并搭配字号输入框；`zoomControls(...)` 使用 70 到 200 的 `Spinner<Integer>`，步进为 10，`zoomValue(...)` 会把已有设置值限制并贴合到这个范围；`receiveDirControls(...)` 使用较长但不撑满整页的接收目录输入框和 `DirectoryChooser` 选择目录。`readSettings(...)` 保存时把设置页已移除的全局传输口令写为空字符串，避免继续保存一刀切口令；`saveControls(...)` 左对齐保存按钮并增加上方 20px 间距，读取控件生成新的 `SystemSettings`，调用 `app.controller.updateSettings(...)` 保存，并再次渲染让主题色、字体和缩放立即生效。

## `src/main/java/com/iwmei/lantransfer/view/MainWindow.java`

所属功能：JavaFX 主窗口、路由和共享 UI 组件库。

详细功能：该类继承 `Application`，管理窗口尺寸、标题栏、认证窗口壳、主窗口壳、侧边栏、顶部栏、底部状态栏、页面路由、共享状态和大量复用控件。它持有 `AppController`、当前用户资料、当前系统设置、待传输文件、近期目标、选中目标、当前传输汇总、主题色、发送运行/暂停状态、传输日志自动滚动状态和用户列表分页状态，并负责在本机忙碌状态收到文件请求时弹出接收确认框，在收到带口令的传输请求时弹出口令框，以及用 toast 展示接收端进度节点。它还负责系统托盘图标、启动后最小化到托盘、最小化按钮隐藏到托盘和传输完成提示音。共享头像控件支持首字头像和 Base64 图片头像，用户卡片会优先显示通过 UDP 接收的个性签名，用户列表中的单人卡片发送按钮使用发送图标并跳转到文件传输页；分组传输对象卡片不显示在线/离线状态。

实现方法：`start(Stage)` 初始化透明窗口后先调用 `controller.setRxAsk(this::confirmReceive)` 和 `controller.setRxProgress(this::showRxProgress)`，把接收前确认、口令校验和接收进度展示回调交给后端，再异步读取系统设置，拿到 `accentColor` 和 `currentSettings` 后进入登录页；读取失败时仍进入登录页。`confirmReceive(fileName, bytes, codeHash)` 是给 `UdpRx` 后台线程调用的同步确认方法，如果已经在 JavaFX 线程就直接显示确认框，否则用 `Platform.runLater(...)` 切回 UI 线程并用 `CompletableFuture` 等待最多 30 秒，超时或异常按拒收处理。`showReceiveConfirm(...)` 在 `codeHash` 为空时构造普通 JavaFX `Alert` 确认框，在 `codeHash` 非空时调用 `showCodeConfirm(...)` 构造密码输入框；两种弹窗都会调用 `styleDialog(...)` 加载 `app.css`、加入 `.dark-dialog` 并注入当前主题色，避免系统默认浅色弹窗导致深色界面里文字看不清；`showCodeConfirm(...)` 用 `codeMatches(...)` 把用户输入口令的 SHA-256 和发送端摘要比较，只有匹配时 OK 按钮才可点。`showRxProgress(...)` 接收文件名和百分比，达到 100% 时调用 `playDoneSound()`，再用 toast 显示进度。`playDoneSound()` 读取 `currentSettings.soundOnComplete()`，为 true 时调用 JDK `Toolkit.beep()`。`showAuth(...)`、`showFileTransferPage()`、`showUserListPage()`、`showProfilePage()`、`showSettingsPage()` 等方法只做页面路由转发；独立扫描页已删除，扫描触发和反馈由 `UserList.scanUsers()` 在用户列表页内完成。`setAuthPage(...)` 和 `setMainPage(...)` 把页面内容放入统一窗口壳，`setWindow(...)` 负责 Scene、CSS、尺寸和首次显示，并在每次换页后调用 `applyStartupTray()`；`applyStartupTray()` 只在用户已登录、还没有执行过、并且同时启用开机自启动和启动后最小化时调用 `hideToTray()`，避免手动登录成功后窗口被自动隐藏而像程序崩溃。`titleBar()` 的最小化按钮调用 `minimizeWindow()`，设置启用时隐藏到托盘，否则执行普通最小化。`ensureTray()` 用 JDK `SystemTray` 创建托盘图标和“显示/退出”菜单，`restoreFromTray()` 回到 JavaFX 线程显示窗口，`exitFromTray()` 移除托盘图标并退出，`trayImage()` 用当前主题色生成 16px 圆点图标。`appRoot(...)` 调用 `rootStyle(...)` 把主题色、字体、字号和缩放倍率写入根节点 CSS；缩放用 `fontSize * zoomPercent / 100` 计算全局字号，避免直接缩放窗口导致裁剪。`windowShell(...)`、`mainWindowShell(...)`、`titleBar()`、`sidebar(...)`、`mainTopbar()` 和 `statusFooter()` 组成应用外框；`sidebar(...)` 只保留文件传输、用户列表、我的和系统设置四个主导航项，不再临时插入扫描页导航项。`mainTopbar()` 用 `titleLabel(displayName(), 14)` 显示当前用户昵称，并调用 `avatar(displayInitial(), "#c8d1dc", 34, profile.avatar())` 让右上角小头像继承“我的资料”上传头像，未登录或头像为空时显示首字头像。`statusFooter()` 不再显示静态样例，传输模式由 `transferMode()` 读取 `currentSettings.groupCode()` 判断“公开局域网/口令组”，当前网速由 `currentSpeed()` 遍历 `currentSummary.tasks()` 中状态为“传输中”的任务并通过 `speedBytes(...)`、`speedText(...)` 汇总格式化，存储位置直接读取 `currentSettings.receiveDir()`，更改按钮跳转设置页。`connectionInfo()` 负责左下角状态信息，本机名来自 `profile.deviceName()`，IP 来自 `currentSettings.ipv4()`，状态文字来自 `userStatusText()`，状态点由 `userDeviceStatus()` 把 `Profile.status()` 映射成在线或离线显示。`cardGrid(...)`、`addCard(...)`、`userCard(...)`、`addRecentTarget(...)`、`tableGrid(...)`、`addTransferRow(...)` 等提供业务页面共享布局；`addTransferRow(...)` 接收当前任务以及重试、移除两个回调，`operationCell(...)` 在失败状态下把“重试/移除”按钮绑定到这两个回调，避免表格按钮只有外观没有动作。`userCard(device, large)` 委托到 `userCard(device, large, false)`，`userCard(device, large, pickMode)` 在普通用户卡片第一行调用 `userCardTitle(...)` 显示“设备名称 | 昵称”，本机由 `isSelfDevice(...)` 判断并加 `(本机)`，`isSelfDevice(...)` 使用 `LanPeer` 生成的 `lastSeen=本机` 判断，避免同账号多实例因为设备ID不同而丢失本机标记，分组目标仍保留原先“组：分组名/成员数”的近期对象展示。用户卡片第二行由 `userCardSubTitle(...)` 返回签名，组目标返回成员数字段；只有真实单个用户才追加 `statusLine(...)`，分组对象不再显示在线/离线小标签。大卡片非建组模式会添加 `mdi2s-send` 图标按钮，点击后调用 `addRecentTarget(device)` 并进入文件传输页；`addRecentTarget(...)` 只去重后追加对象并选中，不再超过 5 个就删除旧对象。小卡片添加近期对象的 `-` 移除按钮，建组模式通过 `pickMode=true` 隐藏发送按钮且不会落入小卡片分支，给 `UserList` 叠加复选框时不会再出现多余的 `-`。`transferRunning` 和 `transferPaused` 是文件传输页的共享运行状态，防止重复提交并控制暂停按钮文案；`autoScrollLogs` 保存传输日志自动滚动复选框状态。`titleLabel(...)`、`mutedLabel(...)` 和 `accentLabel(...)` 只设置字号和字重，字体族从 `rootStyle(...)` 继承，避免系统设置改字体后标签仍固定为微软雅黑；`statusLine(...)` 的状态文字同样只设置字号。`ipColumn(...)` 使用 `mdi2c-content-copy` 图标按钮复制 IP，toast 文案为“已复制”加字段名，不在中文和技术缩写之间加空格。`avatar(String,String,double)` 保留首字头像入口；`avatar(String,String,double,String)` 在头像 Base64 非空时调用 `imageAvatar(...)` 解码成 JavaFX `ImageView` 并用圆形 clip 裁成圆头像，头像数据异常时回到首字头像，防止坏数据打断页面渲染。`textField(...)`、`passwordField(...)`、`primaryButton(...)`、`secondaryButton(...)`、`outlineButton(...)`、`ghostTextButton(...)`、`compactButton(...)`、`iconToggleButton(...)` 和 `textButton(...)` 统一控件样式。`avatar(...)`、`statCard(...)`、`statusCard(...)`、`progressCell(...)`、`operationCell(...)`、`statusBadge(...)` 和 `logLine(...)` 构造具体视觉组件。`copyToClipboard(...)` 和 `toast(...)` 处理用户反馈。
