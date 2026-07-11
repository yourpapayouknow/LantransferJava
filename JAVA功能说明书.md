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

当前暂无按规则跳过的核心后端功能。

## `src/main/java/com/iwmei/lantransfer/App.java`

所属功能：应用启动入口。

详细功能：`App` 只负责把 JVM `main` 方法交给 JavaFX，启动 `MainWindow`。它不保存业务状态，不创建服务对象，也不参与页面路由。

实现方法：`main(String[] args)` 调用 `Application.launch(MainWindow.class, args)`，由 JavaFX 生命周期继续执行 `MainWindow.start(Stage)`。这个文件保持极薄入口，后续不要把登录、网络、传输初始化塞进这里；需要启动时初始化时，优先放到控制器或后端服务里。

## `src/main/java/com/iwmei/lantransfer/controller/AppController.java`

所属功能：界面和业务服务之间的控制层。

详细功能：`AppController` 持有一个 `BackendFacade` 实例，给 JavaFX 页面提供登录、注册、加载已记住账号、近期设备、全部设备、保存传输分组、扫描设备、加载设置、启动传输、启动传输并接收进度快照、暂停/继续发送、设置接收前确认回调、设置接收进度回调、更新资料、更新状态和更新设置的统一入口。当前实现实例化 `LocalBackend`，登录注册和“记住我”走本地真实账号仓库，局域网扫描、设置保存、分组展开、暂停发送和传输报告走本地实现；`MockBackendFacade` 只保留给独立前端联调，不再参与主 App 流程。

实现方法：每个公开方法都只做转发，例如 `login(LoginRequest)` 返回 `backend.login(request)` 的 `CompletableFuture<AuthResult>`，`loadRememberedAccount()` 返回本地最近登录账号，`saveGroup(name, members)` 把用户列表当前选中成员交给后端保存并返回组目标，`loadSettings()` 返回 `backend.loadSettings()`，`startTransfer(files, targets, progress)` 把页面传来的 `Consumer<TransferSummary>` 继续交给后端，`pauseTransfer(boolean)` 把暂停状态传给发送服务，`setRxAsk(RxAsk)` 把主窗口的接收确认逻辑交给 `UdpRx`，`setRxProgress(RxProgress)` 把主窗口的接收进度展示逻辑交给 `UdpRx`，`updateProfile(Profile)`、`updateStatus(UserStatus, String)` 和 `updateSettings(SystemSettings)` 直接转给后端。薄控制器保证页面层不直接知道 `acco` 账号表、设置文件、UDP 传输或扫描实现。

## `src/main/java/com/iwmei/lantransfer/service/BackendFacade.java`

所属功能：前后端交互接口。

详细功能：该接口定义 UI 当前需要的业务能力，包括登录、注册、加载已记住账号、加载近期传输对象、加载全部设备、保存本地传输分组、扫描局域网设备、加载系统设置、启动传输、启动传输并接收传输中进度快照、暂停或继续当前发送任务、设置接收前确认回调、设置接收进度回调、更新资料、更新状态和更新设置。它是页面层和真实业务实现之间的边界。

实现方法：查询和长耗时操作返回 `CompletableFuture`，便于 JavaFX 页面在完成后用 `Platform.runLater` 回到 UI 线程；纯写入接口目前是同步 `void`，后续如果写文件或网络操作变慢，应改成 `CompletableFuture<Void>`。`loadRememberedAccount()` 只返回账号字符串，不返回密码；`saveGroup(...)` 返回一个可放入近期传输对象的组目标；`loadSettings()` 是设置页进入时读取 `SystemSettings` 的入口。带 `Consumer<TransferSummary>` 的 `startTransfer(...)` 是实时进度入口，默认实现回退到旧的两参数方法，保证 `MockBackendFacade` 等旧实现不必强制改写也能编译。`pauseTransfer(boolean)` 默认空实现，真实后端会把它交给发送器；`setRxAsk(RxAsk)` 是忙碌状态接收前确认入口，`setRxProgress(RxProgress)` 是接收端进度展示入口，二者默认空实现让临时后端不用处理接收弹窗和进度提示。新增后端功能时先判断 UI 是否真的需要接口，避免提前铺接口。

## `src/main/java/com/iwmei/lantransfer/service/RxAsk.java`

所属功能：接收前确认回调接口。

详细功能：`RxAsk` 是 `UdpRx` 和 JavaFX 主窗口之间的最小确认边界。它只表达“某个文件是否允许开始接收”，用于本机状态为 `UserStatus.BUSY` 时先询问用户，再决定是否 ACK 文件开始包。

实现方法：接口使用 `@FunctionalInterface`，只有 `approve(String fileName, long bytes)` 一个方法，返回 true 表示允许接收，返回 false 表示拒收。`MainWindow` 传入会弹确认框的实现，`UdpRx` 在后台线程中调用；测试可以传入直接 true 或 false 的 lambda 来验证同意和拒绝路径。

## `src/main/java/com/iwmei/lantransfer/service/RxProgress.java`

所属功能：接收进度回调接口。

详细功能：`RxProgress` 是 `UdpRx` 向界面展示接收端进度的最小边界。它只表达文件名和接收百分比，不绑定 `TransferTask`，避免为了接收端提示硬凑发送目标对象。

实现方法：接口使用 `@FunctionalInterface`，只有 `update(String fileName, int percent)` 一个方法。`UdpRx.RxFile` 在分片写入后按 25% 阈值和最终 100% 调用它，`MainWindow` 传入 toast 展示实现，测试传入收集字符串的 lambda 验证 100% 回调。

## `src/main/java/com/iwmei/lantransfer/service/MockBackendFacade.java`

所属功能：前端联调用假数据后端。

详细功能：当前类内置一个带默认状态的 `Profile`、18 个 `UserDevice` 和一份默认 `SystemSettings`，只供独立前端联调使用，不由 `AppController` 主流程实例化。它支持假登录、假注册、假记住账号、假近期设备、假全部设备、保存临时分组目标、假扫描设备、固定设置读取和固定传输结果。登录只接受 `admin/admin`，记住账号固定返回 `admin`，注册总是返回“注册申请已提交”，传输总是返回固定的 4 条任务和 5 条日志。

实现方法：所有方法都用 `CompletableFuture.completedFuture(...)` 立即返回，模拟异步后端但不做真实 IO。`loadRememberedAccount()` 返回 `admin` 只服务 Mock 登录场景，真实登录页不会从这里取默认密码；`loadRecentDevices()` 返回前 5 个设备，`saveGroup(...)` 只构造一个 `UserDevice.group(...)` 供前端联调分组卡片，`scanLanDevices()` 返回前 4 个设备，`loadSettings()` 返回固定 IP、限速、重试次数和主题配置，`startTransfer(...)` 会在未选择目标时使用前 5 个设备作为 Mock 目标，并根据目标数量构造 `TransferSummary`。该类只能用于独立联调或测试，不能重新接回主 App 流程。

## `src/main/java/com/iwmei/lantransfer/service/AppFiles.java`

所属功能：本地数据目录工具。

详细功能：统一决定设置、近期对象、本地登录状态等运行数据放在哪里。默认把数据放在用户目录 `.lantransfer/<仓库名>/` 下，避免把运行状态误提交到 Git；进行宿主机多实例、虚拟机联调或自动化 GUI 传输测试时，可以通过 `lantransfer.dataDir` JVM 参数或 `LANTRANSFER_DATA_DIR` 环境变量为每个运行端指定独立数据目录，隔离设置、近期对象、接收目录和“记住我”状态。账号主凭据不放在这里，而是放在根目录 `acco` 并由 GitHub Actions 处理注册请求。

实现方法：`dataDir()` 先调用 `configuredDataDir()` 读取 `System.getProperty("lantransfer.dataDir")`，如果 JVM 参数为空再读取 `System.getenv("LANTRANSFER_DATA_DIR")`；只要覆盖值非空，就直接把它转成 `Path` 返回。没有覆盖值时，才使用 `System.getProperty("user.home")`、`.lantransfer` 和 `repoSlug()` 组合默认路径。`repoOrigin()` 读取 `.git/config` 中的 origin 地址，失败时返回 `LantransferJava`。`repoSlug()` 从 origin URL 取最后一段仓库名，去掉 `.git` 并清理非法路径字符。`AuthStore` 只通过它定位本机 `la` 登录状态，`SettingsStore` 和 `RecentStore` 通过它定位设置与近期对象；多端测试时必须给不同端传入不同目录，避免两个 JavaFX 实例共享本地运行状态。

## `src/main/java/com/iwmei/lantransfer/service/AutoStart.java`

所属功能：Windows 开机自启动管理。

详细功能：`AutoStart` 负责把系统设置里的“开机自启动”落到 Windows 当前用户启动目录。启用时创建 `极速互传.cmd`，关闭时删除该脚本；生产默认构造只在 Windows 系统上生效，非 Windows 系统不做副作用。它不修改注册表，不需要管理员权限，适合课程项目和本机演示。

实现方法：默认构造器通过 `%APPDATA%/Microsoft/Windows/Start Menu/Programs/Startup` 定位启动目录，`sync(boolean)` 根据开关创建目录、写入脚本或删除脚本。脚本先切到当前项目根目录，再优先运行 `mvnw.cmd -q javafx:run`，没有 Maven Wrapper 时运行 `mvn -q javafx:run`，并用 `start /min` 尽量最小化启动命令窗口。`AutoStart(Path)` 允许自检把启动脚本写到临时目录，`none()` 给设置仓库测试使用，避免测试污染真实系统启动项。

## `src/main/java/com/iwmei/lantransfer/service/AuthStore.java`

所属功能：无服务器登录注册账号仓库。

详细功能：`AuthStore` 负责第一屏登录与注册的真实后端逻辑，也负责“我的”页面资料、状态保存和“记住我”账号回填。账号主凭据来自仓库根目录短名账号表 `acco`，注册时本地程序生成 `req/<账号>` 请求文件并直接 push 到当前 GitHub 远程仓库，`.github/workflows/acco.yml` 的 GitHub Actions 会自动把请求合入 `acco` 并删除请求文件，相当于自动审核通过。普通用户机器没有仓库拥有者凭据时，push 优先使用运行时注入的辅助账号 token：JVM 参数 `-Dacco.t=...` 或环境变量 `ACCO_T`；没有 token 时才退回本机 Git 凭据。账号表保存账号、盐、PBKDF2 密码摘要、用户 ID、昵称、设备名、签名、注册时间、最近登录时间、语言、状态和审核字段；不保存明文密码。`la` 只保存本机“记住我”状态，不再作为主凭据库。辅助账号邮箱密码不能作为 GitHub API 或 Git push 凭据写入代码，token 也不能提交到仓库。

实现方法：`login(LoginRequest)` 先用 `pullAccounts()` 执行 `git pull --rebase --autostash origin <当前分支>` 拉取远程最新 `acco`，再用 `readAccounts()` 读取 CSV 表头之后的账号行；账号不存在返回失败，密码摘要不匹配返回失败，匹配时只在内存中更新 `lastLoginAt` 用于本次 `Profile`，并把“记住我”写入本机 `la`。`register(RegisterRequest)` 校验账号、密码和重复账号后，用 `putAccount(...)` 生成盐、PBKDF2 摘要和资料字段，用 `approveRegistration(...)` 写入 `reviewStatus=AUTO_APPROVED` 与 `reviewApprover=actions`；启用 Git 同步时调用 `saveReq(...)` 写入 `req/<账号>`，再用 `pushPath(...)` 只暂存该请求文件、调用 `ensureGitIdentity()` 补齐最小 Git 提交身份、提交短消息 `acco req` 并推送。`push(...)` 会先调用 `pushUrl()` 根据 `AppFiles.repoOrigin()` 提取 `owner/repo`，如果存在 `-Dacco.t` 或 `ACCO_T`，就构造一次性 HTTPS token 地址执行 `git push <临时地址> HEAD:<当前分支>`；如果没有 token，就执行普通 `git push origin <当前分支>`。`clean(...)` 会在错误输出中遮盖 token 或 URL 编码后的 token，避免失败信息把密钥显示给界面。推送后 `waitForAction(...)` 每 5 秒拉取一次远程，最多等待 45 秒；如果 Actions 已把账号合入 `acco`，返回注册成功并提示登录，否则返回 `pendingReview=true` 进入等待页。测试构造器会关闭 Git 同步，直接写临时 `acco`，避免自检访问远程。`updateProfile(Profile)` 和 `updateStatus(UserStatus, String)` 仍通过 `userId` 找到账号行，更新资料或状态后直接提交推送 `acco`；如果 Git 暂存区已有其它改动，`pushPath(...)` 会拒绝操作，避免把无关文件带入账号提交。

## `src/main/java/com/iwmei/lantransfer/service/LocalBackend.java`

所属功能：当前主后端组合实现。

详细功能：`LocalBackend` 是 `AppController` 当前使用的真实后端入口。它把登录、注册、记住账号、资料保存和状态保存交给 `AuthStore`，把系统设置读取和保存交给 `SettingsStore`，把近期传输对象读取和保存交给 `RecentStore`，把本地传输分组保存和分组目标展开交给 `GroupStore`，启动 `UdpRx` 后台接收服务，把接收前确认回调、接收进度回调和本机用户状态同步给 `UdpRx`，把传输任务创建、暂停/继续和发送端进度快照交给 `UdpTx`，把局域网扫描和已发现设备列表交给 `LanPeer`，并在登录、资料修改和状态切换后刷新本机发现信息。传输请求通过单线程后台队列执行，避免用户连续点击或页面重复提交时多个传输任务同时竞争 UDP 发送和近期对象写入。

实现方法：构造器先读取设置中的 `groupCode` 并调用 `lan.updateGroup(...)`，再调用 `rx.start()`，让应用启动后立即监听 `LanPeer.TRANSFER_PORT`。`login(...)`、`register(...)` 和 `loadRememberedAccount()` 使用 `CompletableFuture.supplyAsync(...)` 执行账号表拉取、注册请求推送和本地 `la` 读取，避免阻塞 JavaFX 事件线程；登录成功后调用 `lan.updateSelf(profile)` 和 `rx.updateStatus(profile.status())`，让发现协议和接收门禁同时使用当前账号状态。`setRxAsk(RxAsk)` 调用 `rx.setAsk(...)` 保存主窗口确认回调，`setRxProgress(RxProgress)` 调用 `rx.setProgress(...)` 保存主窗口接收进度回调。`updateProfile(...)` 通过 `AuthStore` 更新并推送 `acco` 后刷新 `LanPeer` 本机资料，如果资料不为空也把状态同步给 `UdpRx`；`updateStatus(...)` 保存状态后同时调用 `lan.updateStatus(...)` 和 `rx.updateStatus(...)`，让 ONLINE/BUSY/INVISIBLE/OFFLINE 同时参与扫描、发送策略和接收门禁。`loadSettings()` 异步读取 `SettingsStore.load()`，`updateSettings(...)` 写入 `SettingsStore.save(...)` 后再次调用 `lan.updateGroup(...)`，让传输口令立即影响后续扫描和响应。`loadRecentDevices()` 只返回 `GroupStore.targets()` 与 `RecentStore.load()` 中未重复的真实本地对象；为空时交给前端空态和发送前校验处理。`saveGroup(...)` 保存选中成员快照并返回组目标，前端会把它加入近期传输对象。`loadAllDevices()` 直接读取 `LanPeer.knownDevices()`，没有发现其它设备时只返回本机或空结果，不再补演示用户。`scanLanDevices()` 异步调用 `LanPeer.scan()`，实际发 UDP 广播并等待同程序响应。`transferQueue` 使用 JDK `Executors.newSingleThreadExecutor(...)` 创建 daemon FIFO 队列；两参数 `startTransfer(...)` 传入空进度回调以兼容旧调用，三参数 `startTransfer(...)` 通过 `CompletableFuture.supplyAsync(..., transferQueue)` 排队执行，先把空目标转成空列表，再用 `GroupStore.expand(...)` 把组目标展开成真实成员，随后调用 `UdpTx.run(files, safeTargets, settings, progress)`，最后用用户原始请求目标调用 `RecentStore.remember(...)` 保存近期对象。`pauseTransfer(boolean)` 直接调用 `UdpTx.setPaused(...)`，影响当前发送器后续 UDP 包发送。

## `src/main/java/com/iwmei/lantransfer/service/SettingsStore.java`

所属功能：系统设置本地仓库。

详细功能：负责读取和保存系统设置页中的 IP、上传/下载限速、最大重试次数、主题色、字体、字号、缩放比例、接收目录、传输口令、语言和启动选项。它使用 `AppFiles.dataDir()/settings.properties`，不需要数据库；保存时会把“开机自启动”同步给 `AutoStart`，让设置项具备系统级效果。

实现方法：`load()` 先构造默认设置；如果设置文件不存在或读取失败，就直接返回默认值。存在文件时用 `Properties` 读取各字段，整数读取失败时使用默认值，布尔值用 `Boolean.parseBoolean(...)` 解析，传输口令读取 `groupCode`，缺失时回退空字符串表示公开组。`save(SystemSettings)` 把设置写回 properties 文件，记录 `repo.origin` 方便定位来源，然后调用 `autoStart.sync(value.autoStart())` 创建或删除启动目录脚本。默认构造器使用真实 `AutoStart`，测试构造器使用 `AutoStart.none()` 避免系统副作用。默认 IP 由 `localIp(boolean ipv6)` 遍历启用的非回环网卡获取；找不到时 IPv4 回退 `127.0.0.1`，IPv6 回退 `::1`。默认接收目录来自 `SystemSettings.defaultReceiveDir()`。

## `src/main/java/com/iwmei/lantransfer/service/RecentStore.java`

所属功能：近期传输对象本地仓库。

详细功能：`RecentStore` 负责把最近传输过的目标设备保存到本地 `recent.properties`，让文件传输首页重启后仍能显示真实近期对象，而不是只依赖内存或演示数据。它保存 `UserDevice` 的 ID、昵称、设备名、在线状态、用户状态、最近传输时间、头像字段、目标地址和传输端口，最多保留 12 个。

实现方法：默认构造器使用 `AppFiles.dataDir().resolve("recent.properties")` 定位文件。`load()` 用 `Properties` 读取 `count` 和每个序号下的设备字段，字段缺失时使用安全默认值，设备在线状态解析失败时回退 `DeviceStatus.OFFLINE`，用户状态解析失败时回退 `UserStatus.DEFAULT`。`remember(List<UserDevice>)` 把本次目标更新时间后放到 `LinkedHashMap` 前面，再追加旧记录并按 ID 去重，最后截取前 12 个写回。`save(...)` 创建父目录并写入 properties，同时记录 `repo.origin`；`put(...)` 会保存 `userStatus`，`touched(...)` 只更新时间文本并保留用户状态。该实现用本地文件替代数据库，足够满足无服务器课堂项目的近期对象恢复。

## `src/main/java/com/iwmei/lantransfer/service/GroupStore.java`

所属功能：本地传输分组仓库。

详细功能：`GroupStore` 负责用户列表中的“选中建组”和文件传输页中的组目标展开。它把分组名和组内用户快照保存到 `groups.properties`，读取时生成 `UserDevice.group(...)` 形式的组目标；当文件传输页选择组作为近期传输对象时，后端会把该组展开成组内所有真实成员，再交给 `UdpTx` 非阻塞并发发送。分组保存的是用户当时的网络地址、端口、状态和头像信息，不依赖服务器，也不修改局域网发现协议。

实现方法：默认构造器使用 `AppFiles.dataDir().resolve("groups.properties")` 定位文件；测试构造器允许指定临时文件。`save(name, members)` 先用 `UserDevice.cleanGroupName(...)` 清洗组名，再用 `cleanMembers(...)` 去掉空值、组目标和重复成员，成员为空时抛出异常；写入成功后返回 `UserDevice.group(groupName, memberCount)` 供前端加入近期对象。`targets()` 读取所有分组并返回组目标列表。`expand(targets)` 遍历用户选择的目标，普通用户原样保留，组目标按 `groupName()` 查表展开，使用 `LinkedHashMap` 按 ID 去重，避免同一用户既被单独选中又在组里时重复发送。`loadGroups()` 和 `writeGroups(...)` 使用 Java `Properties` 保存 `count`、组名、成员数量和每个成员字段；设备状态和用户状态解析失败时分别回退 `OFFLINE` 和 `DEFAULT`。

## `src/main/java/com/iwmei/lantransfer/service/LanPeer.java`

所属功能：局域网设备发现后端。

详细功能：`LanPeer` 负责实验报告中的“利用广播或组播发现局域网内其他运行本程序的主机”。它维护本机设备信息、已发现设备表、最后发现时间和传输口令分组摘要，启动后台 UDP 响应线程，扫描时向所有可用广播地址和本机回环地址发送发现消息，并把收到的同程序响应转换成 `UserDevice`。发现请求和响应会携带口令 SHA-256 摘要，只有同一口令的设备互相发现；发现响应还携带真实传输 IP、传输端口和 `UserStatus`，为 `UdpTx/UdpRx` 建立目标地址并提供状态条件传输依据。发现端口默认是 `45331`，传输端口默认是 `45332`；宿主机多实例或虚拟机联调时，发现端口可通过 `lantransfer.discoveryPort` / `LANTRANSFER_DISCOVERY_PORT` 覆盖，扫描端口集合可通过 `lantransfer.discoveryPorts` / `LANTRANSFER_DISCOVERY_PORTS` 配置，传输端口可通过 `lantransfer.transferPort` / `LANTRANSFER_TRANSFER_PORT` 覆盖，让不同运行端既能避开本机端口冲突，又能互相扫描到对方。已发现设备超过离线阈值未再次出现时，会在用户列表中标记为离线。当前用户切到隐身或离线状态时，本机不响应发现请求。

实现方法：构造器生成本机 `UserDevice` 并通过 `remember(...)` 放入 `seen` 和 `seenAt`，默认启动 daemon 响应线程；生产离线阈值为 30 秒，测试构造器可传入更短阈值。静态字段 `PORT` 由 `configuredPort("lantransfer.discoveryPort", "LANTRANSFER_DISCOVERY_PORT", 45331)` 初始化，静态字段 `TRANSFER_PORT` 由 `configuredPort("lantransfer.transferPort", "LANTRANSFER_TRANSFER_PORT", 45332)` 初始化；`configuredPort(...)` 先读 JVM 参数，再读环境变量，解析为正整数就使用配置值，解析失败或未配置时回退默认端口。`SCAN_PORTS` 由 `configuredScanPorts()` 初始化，始终包含当前发现端口，并额外解析 `lantransfer.discoveryPorts` 或 `LANTRANSFER_DISCOVERY_PORTS` 中用逗号、分号或空白分隔的端口列表。`updateGroup(String)` 会把传输口令清洗后计算 SHA-256 十六进制摘要，空口令得到空摘要并表示公开组。`scan()` 创建临时 `DatagramSocket`，向 `127.0.0.1`、`255.255.255.255` 和所有网卡广播地址发送 `discoverMessage()`，并且对 `SCAN_PORTS` 中每个端口都发一次；实际发送由 `sendDiscover(...)` 执行，单个地址或端口发送失败只跳过当前目标，不会阻断其他地址，也不会跳过后续接收阶段；公开组发送 `LANTRANSFER_DISCOVER_V1`，非空口令发送 `LANTRANSFER_DISCOVER_V1\t口令摘要`，并在约 900ms 内接收响应、调用 `parse(...)` 与 `remember(...)` 更新时间。后台 `replyLoop()` 绑定当前 `PORT`，收到发现消息后先检查 `groupMatches(discoverGroup(message))` 和 `discoverable(self.userStatus())`，口令不同、隐身和离线都不回复；允许发现时用 `encode(self)` 回复发送方，收到设备响应时也会解析并缓存；如果监听线程因端口占用、权限或网络栈异常退出，会把异常打印到进程错误日志，避免 GUI 扫描失败时没有线索。响应协议是制表符分隔的短文本：`LANTRANSFER_HERE_V1\t设备ID\t昵称\t设备名\t主机地址\t传输端口\t用户状态\t口令摘要`；`parse(message, fallbackHost)` 仍兼容旧 4 到 7 字段响应，缺状态时回退 `UserStatus.DEFAULT`，缺口令摘要时只会被公开组接收。`updateSelf(Profile)` 在登录或资料修改后用账号资料刷新本机发现身份，`updateStatus(UserStatus)` 在状态切换后刷新本机 `UserDevice`。`knownDevices()` 通过 `sorted()` 返回设备时会调用 `withStatus(...)`，按最后发现时间和用户状态生成 ONLINE/OFFLINE 及“刚刚/秒前/分钟/对方隐身/对方离线/已离线”展示文本。`broadcastAddresses()` 会加入回环地址和全局广播地址，再从 `NetworkInterface` 读取可广播地址；回环地址用于没有 VM 权限时的本机双实例测试，真实局域网仍依赖广播地址。`localDevice()` 用系统用户名、主机名、本机 IPv4 和当前传输端口生成本机条目。该功能不需要服务器；如果防火墙或网段策略拦截 UDP，扫描结果至少保留本机。

## `src/main/java/com/iwmei/lantransfer/service/UdpRx.java`

所属功能：UDP 文件接收后端。

详细功能：`UdpRx` 负责真实文件接收的后台服务。它默认监听 `LanPeer.TRANSFER_PORT`，接收 `UdpTx` 发来的文件开始包和文件内容分片包；该端口默认是 `45332`，可由 `lantransfer.transferPort` JVM 参数或 `LANTRANSFER_TRANSFER_PORT` 环境变量覆盖，方便宿主机多实例和虚拟机联调。每个接收文件会创建 `.part` 临时文件和 `.part.meta` 分片状态文件，按分片序号写入正确偏移量，并按接收进度节点调用 `RxProgress` 供界面展示；如果接收端重启后再次收到同一文件的开始包，会从 `.part.meta` 恢复已接收分片并在 BEGIN ACK 中带回缺失分片列表，供发送端定向补发。本机状态为 BUSY 时，`UdpRx` 会在创建接收状态前调用 `RxAsk` 询问用户是否允许接收；本机状态为 INVISIBLE 或 OFFLINE 时直接拒收。收齐全部分片后校验 SHA-256，校验通过才移动为最终接收文件并删除元数据，同时推送 100% 接收进度。接收目录来自 `SettingsStore.load().receiveDir()`，因此设置页保存的新目录会被后续接收任务使用。

实现方法：`start()` 创建 daemon 线程执行 `listen()`，`listen()` 用可复用地址绑定端口并循环接收 UDP 数据包；如果监听线程因端口占用、权限或网络栈异常退出，会把异常打印到进程错误日志，方便 GUI 多端测试时定位失败原因。`updateStatus(UserStatus)` 保存当前本机状态，`setAsk(RxAsk)` 保存接收前确认回调，空回调会回退为自动允许；`setProgress(RxProgress)` 保存接收进度回调，空回调会回退为空操作。协议使用三个短文本头：`LANTRANSFER_FILE_BEGIN_V1` 表示文件开始，携带任务 ID、文件序号、Base64 文件名、文件大小、分片数、分片大小和发送端 SHA-256；`LANTRANSFER_FILE_DATA_V1` 表示文件分片，头部后面直接拼接二进制数据；`LANTRANSFER_FILE_ACK_V1` 是接收端回给发送端的确认。`handleBegin(...)` 创建 `RxFile` 接收状态，如果同一任务文件已经在本轮接收中，就直接用已有 `RxFile.missing()` 生成缺失分片扩展字段并 ACK；新文件会先解析文件名和大小并调用 `allowBegin(...)`，DEFAULT/ONLINE 直接允许，INVISIBLE/OFFLINE 直接拒绝，BUSY 调用 `ask.approve(fileName, size)`，同意后才通过 `createFile(...)` 和 `restoreOrReset()` 读取或创建 `.part`，拒绝时返回 `ACK FAIL` 和 `REJECTED` 扩展字段。`createFile(...)` 会把当前 `RxProgress` 传入 `RxFile`。`uniqueTarget(...)` 会同时避开已存在文件和本轮正在接收的保留路径，避免并发同名文件互相覆盖。`RxFile` 初始化时读取 `.part.meta`，如果 size、chunkCount、chunkSize 和 SHA-256 匹配，就恢复已接收的 `BitSet`；如果不匹配或没有元数据，就清理旧 `.part`。`handleData(...)` 找到对应 `RxFile` 并调用 `write(...)`。`RxFile.write(...)` 使用 `FileChannel` 按 `chunkIndex * chunkSize` 定位写入，`BitSet` 记录哪些分片已经收到，每写入一个新分片就保存 `.part.meta`；未收齐时 `publishProgress()` 根据 `receivedCount * 100 / chunkCount` 按 25% 阈值调用 `progress.update(fileName, percent)`，重复分片不会重复推送；全部收齐后先 `finish()` 计算 SHA-256 并移动最终文件，成功后再 `publish(100)`，校验失败则不推送完成进度。`missing()` 只在已经存在部分分片时返回缺失索引，例如只收到第 0 片且总共 2 片时返回 `1`；完全没有历史分片时返回空字符串，让发送端按普通新任务完整发送。

## `src/main/java/com/iwmei/lantransfer/service/UdpTx.java`

所属功能：UDP 文件发送后端。

详细功能：`UdpTx` 负责把用户选择的真实文件发送到可达目标设备，并返回传输结果页需要的 `TransferSummary`。它支持普通文件和文件夹展开，按目标设备的 `host/port` 目标级并发发送，并按系统设置中的上传限速把总带宽平均分给可发送目标；在线、可达且用户状态为 DEFAULT/ONLINE/BUSY 的设备会进入真实 UDP 发送，离线、缺少地址、隐身和离线状态的设备会生成失败任务和拦截日志。BUSY 目标不会被发送端直接拦截，而是发送 BEGIN 后等待接收端确认。每个目标内部仍按文件顺序发送；每个文件先计算 SHA-256 并发送开始包，如果接收端 BEGIN ACK 返回缺失分片列表，就只补发这些缺失分片，没有缺失列表时按普通新任务发送全部分片。DATA 阶段使用分片 worker 池并发发送，每个 worker 使用自己的 UDP socket 等待自己发送分片的 ACK，接收端按分片索引乱序写入。每个分片 ACK 超时后按系统设置中的最大重试次数重发；多分片文件会在 25%/50%/75% 进度点记录预计剩余时间日志，并通过进度回调推送一条状态为“传输中”的 `TransferSummary` 快照。发送过程中支持暂停和继续：暂停只阻塞后续 UDP 包发送，不破坏已经收到 ACK 的分片状态。

实现方法：两参数 `run(...)` 调用四参数 `run(..., Consumer<TransferSummary>)` 并传入空回调，供旧自检和旧接口继续使用。`setPaused(boolean)` 使用 `pauseLock` 保存暂停状态，继续时 `notifyAll()` 唤醒等待线程；`waitIfPaused()` 在每次 `sendWithAck(...)` 真正发送 UDP 包前执行，暂停期间阻塞，线程被中断时返回失败并保留中断标记。四参数 `run(...)` 先把 `TransferFile` 展开为 `SourceFile` 列表，文件夹用 `Files.walk(...)` 展开为多个普通文件，`sha256(...)` 用 JDK `MessageDigest` 计算每个源文件校验值，`perTargetBytesPerSecond(...)` 把 `SystemSettings.uploadLimit()` 按可发送目标数量折算为每目标字节速率，再调用 `sendTargets(...)` 用标准库固定线程池并发处理多个目标，结果仍按原目标顺序汇总。`sendTarget(...)` 先调用 `sendable(...)` 检查 `DeviceStatus.ONLINE`、真实地址和 `UserStatus.DEFAULT/ONLINE/BUSY`；BUSY 目标会写入“等待接收确认”日志并继续发送，INVISIBLE/OFFLINE 会被拦截为不允许接收。通过检查后才创建 `DatagramSocket` 并连接目标地址，随后逐个文件调用 `sendFile(...)`。`sendFile(...)` 发送 `BEGIN` 元数据包，`beginTimeout(...)` 对所有 BEGIN ACK 统一使用 15 秒等待，因为 BEGIN 是唯一可能触发接收端确认弹窗的阶段，发送端缓存的目标状态可能还是 DEFAULT/ONLINE，但接收端实际已经切到 BUSY；DATA 分片仍使用 500ms 短 ACK 超时保证重试效率。`sendWithAck(...)` 会临时切换 socket 超时、发送包、等待 ACK、失败时按最大重试次数重发，并在 finally 中恢复旧超时；它还会解析 ACK 第 6 个字段并放入 `AckResult.detail()`。`chunksToSend(...)` 把该字段中的逗号分隔缺失分片索引转成待发送列表，字段为空或解析失败时回退发送全部分片。`sendChunks(...)` 是单文件分片并发调度入口，根据待发送分片数量和 CPU 数量创建最多 8 个 worker，并写入“分片并发：N 个 worker”日志；它用 `AtomicInteger cursor` 分配分片索引、`AtomicInteger failedChunk` 记录首个失败分片、`AtomicInteger totalRetries` 汇总重试次数、`AtomicLong sentBytes` 汇总已确认字节。`sendWorker(...)` 为每个 worker 创建独立 `DatagramSocket`，连接同一目标地址和端口，在循环中领取分片、调用 `sendChunk(...)`、等待该 worker socket 收到对应 ACK；不同 worker 的 ACK 不会互相抢。`sendChunk(...)` 用 `FileChannel` 和 `readChunk(...)` 按 `chunkIndex * chunkBytes` 位置读取数据，`readChunk(...)` 使用 `FileChannel.read(buffer, position)` 做并发安全的定位读取，不修改共享 channel 的当前位置。续传时仍可跳过已接收分片，只补缺失分片，并在日志中记录“断点续传：仅补发 N 个缺失分片”。文件开始发送时 `publishProgress(...)` 先推送 0% 快照；每个分片 ACK 后累计已发送字节，`publishChunkProgress(...)` 在 25%/50%/75% 阈值处写日志、计算 ETA、推送包含当前文件、目标、百分比、速度、耗时和日志副本的快照。`RateLimit.pause(...)` 使用同步方法保护目标级限速计数，避免多个分片 worker 同时修改发送字节。最终按文件生成 `TransferTask`，按目标汇总成功数、失败数、重试次数、日志和总耗时。

## `src/main/java/com/iwmei/lantransfer/service/TxSim.java`

所属功能：文件传输结果报告的本地模拟后端。

详细功能：`TxSim` 是早期在真正 UDP 发送内核完成前使用的本地模拟器，目前主后端已改用 `UdpTx`，该类只保留给回归自检。它会读取文件或文件夹大小，按文件和目标组合生成传输列表行，在线目标记为成功，离线目标记为失败并记录三次重试，同时生成开始、每个目标结果和结束日志。

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

详细功能：保存昵称、用户 ID、设备名称、个性签名、注册时间、最后登录时间、版本信息、语言和当前用户状态。文件传输页顶部、我的页面和设置页面都会读取这些信息。

实现方法：使用不可变 `record`，因此修改资料或状态时需要构造新的 `Profile` 并替换 `MainWindow.profile`。保留旧 8 参数构造器，旧调用会自动使用 `UserStatus.DEFAULT`。时间字段使用 `LocalDateTime`，展示时由 `MainWindow.DATE_TIME` 格式化。

## `src/main/java/com/iwmei/lantransfer/model/SystemSettings.java`

所属功能：系统设置数据对象。

详细功能：描述本机 IPv4/IPv6、上传限速、下载限速、最大重试次数、主题色、字体、字号、缩放比例、接收目录、传输口令、语言、开机自启动、启动后最小化和传输完成提示音。当前由 `SettingsStore` 读写，并由系统设置页渲染为可编辑控件。

实现方法：使用 `record` 汇总设置页需要保存的数据。保留一个基础字段构造器，旧调用会自动填充默认接收目录、空传输口令、语言和启动选项。`defaultReceiveDir()` 使用用户目录下的 `极速互传/接收文件`。`SettingsStore.load()` 构造或读取它，`Settings` 页面保存时把控件值重新组装成新的 `SystemSettings` 并调用 `AppController.updateSettings(...)`。

## `src/main/java/com/iwmei/lantransfer/model/UserDevice.java`

所属功能：局域网用户设备数据对象。

详细功能：表示一个可传输目标，包含账号或设备 ID、昵称、设备名、设备在线状态、用户状态、上次在线时间、头像文字、头像颜色、是否使用图片头像、目标主机地址和目标传输端口。它也可以表示本地传输分组目标：分组目标的 ID 使用 `GROUP:` 前缀，显示为“组：分组名”，实际发送前由 `GroupStore` 展开成组内真实用户。

实现方法：使用 `record` 让设备列表、近期对象、扫描雷达和传输任务共享同一数据结构。保留旧 8 参数构造器和带地址的旧构造器，旧调用会自动使用 `UserStatus.DEFAULT`；`reachable()` 用于判断设备是否有真实传输地址。`groupTarget()` 通过 `GROUP:` 前缀判断当前对象是否为分组，`groupName()` 返回前缀后的分组名，`group(String, int)` 创建可显示在近期对象中的分组卡片，`cleanGroupName(String)` 统一清理空组名。`DeviceStatus` 决定在线/离线样式，`UserStatus` 决定对方是否允许被发现或直接接收文件，`lastSeen` 当前是展示文本，后续真实在线检测可改为由服务层生成。

## `src/main/java/com/iwmei/lantransfer/model/TransferFile.java`

所属功能：待传输文件数据对象。

详细功能：保存待发送文件或文件夹的显示名称、大小文本和本地路径。

实现方法：文件选择器和拖拽事件创建 `TransferFile`，其中 `Path` 供图标判断、修改时间读取和后续真实传输读取文件内容使用。大小当前是提前格式化好的文本，避免 UI 反复计算。

## `src/main/java/com/iwmei/lantransfer/model/TransferSummary.java`

所属功能：一次传输结果汇总。

详细功能：保存目标总数、成功数、失败数、重试次数、总耗时、日志列表和任务列表。传输结果页用它展示统计卡、传输列表和日志，也用它执行“清除已完成”和“清空日志”的内存汇总更新。

实现方法：服务层完成或模拟完成传输后返回该 `record`。`logs` 是按顺序展示的文本，`tasks` 是每个目标或文件的进度行。`withoutCompleted()` 过滤掉状态为“已完成”的任务，并用剩余任务重新计算目标数、成功数、失败数和重试数；`withoutLogs()` 保留统计和任务，只把日志列表换成空列表。后续真实 UDP 发送应在最终汇总时把每个目标的确认和重试结果折算进这些字段。

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

## `src/main/java/com/iwmei/lantransfer/util/DeviceSearch.java`

所属功能：用户列表搜索匹配工具。

详细功能：`DeviceSearch` 负责按搜索框输入匹配 `UserDevice` 的昵称、设备名称、设备 ID 和目标主机地址。它不依赖 JavaFX，因此页面和无框架自检都能复用同一套匹配逻辑。

实现方法：`matches(UserDevice, String)` 对空搜索词直接返回 true；非空搜索词用 `Locale.ROOT` 转小写并 `trim()`，再调用 `contains(...)` 分别检查昵称、设备名、ID 和 host。`contains(...)` 对 null 字段返回 false，避免局域网设备缺少地址时触发异常。

## `src/main/java/com/iwmei/lantransfer/view/Auth.java`

所属功能：登录与注册页面。

详细功能：显示认证入口、登录表单、注册表单和注册审核等待页。登录表单会异步读取本地已记住账号并回填账号框，密码框不会从本地文件回填；登录成功后写入 `app.profile` 并进入文件传输页，登录失败显示 toast；注册提交后根据后端结果决定显示错误、审核等待页或返回登录页。

实现方法：`show(boolean)` 根据 `registerMode` 选择 `loginForm()` 或 `registerForm()`。`loginForm()` 创建账号、密码、记住我控件，不写入任何默认演示账号；随后调用 `app.controller.loadRememberedAccount()`，如果有已保存账号，则在 JavaFX 线程回填账号、清空密码并勾选“记住我”。点击登录时构造 `LoginRequest` 调用 `app.controller.login(...)`，异步返回后切回 UI 线程处理结果。`registerForm()` 构造 `RegisterRequest` 调用注册接口；失败时 toast 后端消息，`pendingReview` 为真时进入 `showReviewPending()`，本地注册成功且无需审核时提示“注册成功，请登录”并回到登录页。`showReviewPending()` 保留给以后接入真正审核流程。

## `src/test/java/com/iwmei/lantransfer/service/AuthStoreCheck.java`

所属功能：账号仓库无框架自检。

详细功能：验证第一屏后端最关键路径：旧版自动 `admin/admin` 账号不会继续登录或回填，新账号能注册，本地注册会记录 GitHub Actions 自动审核通过且不进入审核等待，重复注册失败，错误密码失败，正确密码登录成功并返回资料；同时验证“记住我”账号保存和取消勾选清除、资料更新、状态签名和状态枚举会持久化。

实现方法：`main(String[] args)` 创建临时目录，把 `AuthStore` 指向临时 `acco`、`la` 和 `req`，关闭 Git 同步后先确认空账号表不能登录 `admin/admin`，再调用注册并检查 `acco` 表头、账号行、`AUTO_APPROVED`、`actions` 审核标记以及不包含明文密码；随后覆盖重复注册失败、错误密码失败、正确密码登录、记住账号读取、资料更新、状态更新、再次登录和清除记住账号接口。最后用反射调用 `repoPath(...)` 验证 HTTPS 与 SSH 远程地址都能解析成 `owner/repo`，并临时设置 `acco.t` 验证 `clean(...)` 会遮盖原始 token 和 URL 编码 token，避免 Git 失败输出泄漏密钥。运行方式是先编译测试类，再执行 `java -cp target/classes;target/test-classes com.iwmei.lantransfer.service.AuthStoreCheck`。

## `src/test/java/com/iwmei/lantransfer/service/AutoStartCheck.java`

所属功能：开机自启动脚本无框架自检。

详细功能：验证 `AutoStart` 能在指定目录创建启动脚本、脚本内容包含 JavaFX 启动命令，并能在关闭自启动时删除脚本。它只使用临时目录，不会写入真实 Windows 启动目录。

实现方法：`main(String[] args)` 创建临时目录，用 `new AutoStart(dir)` 指向该目录，先调用 `sync(true)` 并检查 `scriptPath()` 存在和脚本内容，再调用 `sync(false)` 检查脚本删除，最后递归删除临时目录。运行方式是先编译测试类，再执行 `java -cp target/classes;target/test-classes com.iwmei.lantransfer.service.AutoStartCheck`。

## `src/test/java/com/iwmei/lantransfer/service/LanPeerCheck.java`

所属功能：局域网发现协议无框架自检。

详细功能：验证 `LanPeer` 的响应文本编码、解析、本机设备兜底、传输地址携带、用户状态携带、传输端口 JVM 参数覆盖、同口令可解析、不同口令会被忽略、发现后在线状态和过期离线判定，不依赖真实网络环境。

实现方法：`main(String[] args)` 先设置 `System.setProperty("lantransfer.transferPort", "45432")`，再使用 `new LanPeer(false, 1)` 禁止启动后台 UDP 线程并把离线阈值设为 1 毫秒，构造一个带 `UserStatus.BUSY` 的 `UserDevice`，执行 `encode(...)` 和 `parse(...)` 往返检查，再确认解析后的设备 `reachable()` 为真且用户状态保持 BUSY。随后调用 `updateGroup("team-a")` 编码消息，同组解析应成功；切到 `team-b` 后解析同一消息应返回 null，证明不同口令会被忽略。最后确认 `knownDevices()` 至少包含本机设备，并且本机设备端口包含 45432，证明多实例传输端口覆盖已经进入发现协议；调用 `remember(...)` 记录该设备，立即读取应为 ONLINE，短暂等待后再次读取应为 OFFLINE。失败时 `require(...)` 抛出 `AssertionError`。运行方式是先编译测试类，再执行 `java -cp 'target\classes;target\test-classes' com.iwmei.lantransfer.service.LanPeerCheck`。

## `src/test/java/com/iwmei/lantransfer/service/SettingsStoreCheck.java`

所属功能：系统设置仓库无框架自检。

详细功能：验证默认设置可加载，保存后的上传限速、重试次数、主题色、缩放比例、传输口令、接收目录、语言和提示音设置能够再次读出。

实现方法：`main(String[] args)` 创建临时 properties 路径，先调用 `load()` 检查默认重试次数，再保存一份带 `groupCode` 的自定义 `SystemSettings` 并重新读取，用 `require(...)` 检查关键字段和新字段。最后删除临时文件。运行方式是先编译测试类，再执行 `java -cp target/classes;target/test-classes com.iwmei.lantransfer.service.SettingsStoreCheck`。

## `src/test/java/com/iwmei/lantransfer/service/RecentStoreCheck.java`

所属功能：近期传输对象仓库无框架自检。

详细功能：验证 `RecentStore` 可以保存、读取、去重并置顶近期传输对象，同时保留目标网络地址和用户状态，避免 App 重启后近期目标退回纯演示数据。

实现方法：`main(String[] args)` 创建临时 properties 文件，构造两个在线且用户状态为 BUSY 的 `UserDevice`，先调用 `remember(...)` 保存两个目标，再重复保存第二个目标。检查点包括读取数量为 2、重复保存不会让列表变长、最新目标移动到第一位、最近传输时间不为空、目标地址和端口仍可达、用户状态仍为 BUSY。运行方式是先编译测试类，再在 Windows PowerShell 中执行 `java -cp 'target\classes;target\test-classes' com.iwmei.lantransfer.service.RecentStoreCheck`。

## `src/test/java/com/iwmei/lantransfer/service/GroupStoreCheck.java`

所属功能：传输分组仓库无框架自检。

详细功能：验证 `GroupStore` 可以保存分组、读取组目标、把组目标展开成真实成员，并在同一用户既被单独选择又属于组时去重。它覆盖用户列表“选中建组”和文件传输页“选择组作为传输对象”的最小后端闭环。

实现方法：`main(String[] args)` 创建临时目录和 `groups.properties`，构造两个可达 `UserDevice`，调用 `save("测试组", List.of(one, two, one))` 保存包含重复成员的分组。检查点包括返回对象是组目标、组名可以 roundtrip、`targets()` 能读出一个组、`expand(List.of(group, two))` 后只有两个真实成员，并且成员仍保留 host/port。最后递归删除临时目录。

## `src/test/java/com/iwmei/lantransfer/util/DeviceSearchCheck.java`

所属功能：设备搜索匹配无框架自检。

详细功能：验证用户列表搜索可以命中昵称、设备名、设备 ID 和目标地址，并且不会错误命中无关搜索词。

实现方法：`main(String[] args)` 构造一个带昵称、设备名、ID 和 host 的 `UserDevice`，依次调用 `DeviceSearch.matches(...)` 检查中文昵称、英文设备名大小写、ID 片段、IP 片段和无关姓名。运行方式是先编译测试类，再在 Windows PowerShell 中执行 `java -cp 'target\classes;target\test-classes' com.iwmei.lantransfer.util.DeviceSearchCheck`。

## `src/test/java/com/iwmei/lantransfer/service/TxSimCheck.java`

所属功能：传输模拟器无框架自检。

详细功能：验证一个文件发送给一个在线目标和一个离线目标时，汇总统计、失败重试、任务行、日志数量、清除已完成和清空日志是否正确。

实现方法：`main(String[] args)` 创建临时文件，构造在线和离线两个 `UserDevice`，调用 `new TxSim().run(...)`，用 `require(...)` 检查目标总数为 2、成功为 1、失败为 1、重试为 3、任务行为 2、日志为 4；随后调用 `withoutCompleted()` 检查已完成行被移除且只剩失败行，调用 `withoutLogs()` 检查日志为空，最后删除临时文件。运行方式是先编译测试类，再执行 `java -cp 'target\classes;target\test-classes' com.iwmei.lantransfer.service.TxSimCheck`。

## `src/test/java/com/iwmei/lantransfer/service/UdpWireCheck.java`

所属功能：UDP 发送接收闭环无框架自检。

详细功能：验证 `UdpTx` 和 `UdpRx` 在本机真实 UDP 环境下可以完成目标级并发发送、单文件分片级并发发送、上传限速分配、ACK 确认、SHA-256 完整性校验、忙碌状态接收前确认、ETA 日志、发送端传输中进度快照、接收端进度回调、接收落盘、未完成接收元数据保存和缺失分片定向补发。它覆盖最核心的传输闭环：临时接收目录、临时设置文件、临时源文件、两个本机目标设备、发送汇总统计、重名接收文件处理、接收文件内容一致性、接收端回调出现 100% 完成进度、BUSY 接收端同意后落盘、BUSY 接收端拒绝后失败且不落盘、两分片文件预计剩余时间日志、分片并发日志、进度回调里出现“传输中”任务、半文件 `.part.meta` 保存、重启后从 `.part.meta` 返回缺失分片、发送端只补缺失分片和错误校验拒收。

实现方法：`main(String[] args)` 创建临时目录和源文件，保存一份带接收目录和 10 MB/s 上传限速的 `SystemSettings`，用临时 UDP 端口启动 `UdpRx`，并通过 `setProgress(...)` 把接收进度写入线程安全列表，再构造两个 `127.0.0.1` 目标设备并调用 `new UdpTx(1024).run(...)`。检查点包括每目标限速为 5 MB/s、成功目标数为 2、失败目标数为 0、`hello.txt` 和 `hello-1.txt` 均存在、两个接收文件内容都等于源文件内容，以及接收进度列表包含 `hello.txt:100`。随后创建三个 BUSY 相关接收器：第一个调用 `setAsk((name, bytes) -> true)` 后启动，发送端目标也标记为 BUSY，要求发送成功、日志包含“等待接收确认”、`busy.txt` 落盘；第二个把接收器切到 BUSY 但发送端目标对象仍使用默认状态，`setAsk(...)` 延迟 2.2 秒后同意，用来验证 BEGIN 统一长等待能覆盖近期对象状态缓存过期的 GUI 场景，要求 `stale-busy.txt` 落盘；第三个调用 `setAsk((name, bytes) -> false)` 后启动，要求发送失败且 `deny.txt` 不存在。再用 `new UdpTx(512).run(..., progressSnapshots::add)` 发送两分片文件，确认日志包含“预计剩余”和“分片并发”，并确认回调列表中至少有一条 `TransferTask.status()` 为“传输中”且进度达到 25% 以上的快照。`sendPartial(...)` 手工发送一个只收到首个分片的文件，确认 `partial.bin.part.meta` 包含 `received=0`。续传检查先用 `sendResumePartial(...)` 在旧接收器上留下 `resume.bin.part` 和 `resume.bin.part.meta`，再启动新的 `UdpRx` 并用 `sendResumeBegin(...)` 确认 BEGIN ACK 带回缺失分片 `1`，最后让 `new UdpTx(512)` 真实发送同一文件，要求汇总成功、日志包含“断点续传”、最终 `resume.bin` 内容与源文件一致。最后 `sendBadChecksumBegin(...)` 手工发送一个 SHA-256 错误的空文件开始包，确认接收端返回 `FAIL` 且不会落盘 `bad.txt`。`freePort()` 用临时 `DatagramSocket(0)` 获取可用端口，`deleteTree(...)` 在结束时清理临时目录。运行方式是先编译测试类，再在 Windows PowerShell 中执行 `java -cp 'target\classes;target\test-classes' com.iwmei.lantransfer.service.UdpWireCheck`。

## `src/test/java/com/iwmei/lantransfer/service/UdpPauseCheck.java`

所属功能：UDP 暂停发送无框架自检。

详细功能：验证发送端暂停开关会真正阻塞后续 UDP 包发送，并且继续后同一传输任务能够正常完成，不破坏接收端落盘和内容校验。

实现方法：`main(String[] args)` 创建临时接收目录和设置文件，启动临时端口上的 `UdpRx`，创建一个小源文件和可达目标，先调用 `new UdpTx(512).setPaused(true)`，再用 `CompletableFuture.supplyAsync(...)` 启动发送。等待 300ms 后检查 Future 仍未完成，证明暂停有效；随后调用 `setPaused(false)`，要求 Future 在 5 秒内完成、成功目标数为 1，且接收目录中的文件内容与源文件一致。最后递归删除临时目录。

## `src/main/java/com/iwmei/lantransfer/view/FileTransfer.java`

所属功能：文件传输首页、待发送列表和传输结果页。

详细功能：加载近期传输对象，显示上传文件/文件夹入口，支持拖拽加入待传输项，展示近期目标、传输列表、传输结果统计、传输日志和发送端传输中进度快照。近期对象可以是单个用户，也可以是本地分组；选择分组发送时后端会展开组内成员并对所有成员并发发送。它也是从登录后进入的第一个主功能页。当前首页传输列表不再固定显示演示任务，而是显示 `app.currentSummary` 中的真实本地传输结果；没有结果时只显示空表头和 0 计数。传输开始后页面会先进入结果页显示空汇总，后续收到后端进度回调时刷新“传输中”行，最终 Future 完成后再刷新为完整结果。传输过程中会显示“暂停发送/继续发送”按钮。传输结果页的“清除已完成”和“清空日志”按钮会直接更新当前内存汇总并刷新页面。

实现方法：`showFileTransferPage()` 调用控制器加载近期设备，首次进入时填充 `app.recentTargets`，然后把 `app.currentSummary == null ? List.of() : app.currentSummary.tasks()` 传给 `transferListSection(...)`。`uploadStrip()` 绑定文件选择、文件夹选择、拖拽进入/离开/释放事件，并按 `app.pendingFiles` 动态显示开始发送和清除按钮；发送中会禁用开始和清除按钮，并通过 `pauseButton()` 显示当前暂停状态。`pendingFileCard(...)` 使用 `FileIcons` 展示图标、大小和修改时间。`transferListSection(...)` 根据任务状态动态计算全部、进行中、已完成和已失败数量；已完成数量为 0 或没有汇总时禁用“清除已完成”。`clearCompletedTasks()` 调用 `TransferSummary.withoutCompleted()` 后重绘结果页，`clearLogs()` 调用 `TransferSummary.withoutLogs()` 后重绘结果页。`startTransfer()` 校验待传输项和目标列表，`hasUsableTarget(...)` 要求至少有一个真实可达用户或组目标，未选择有效对象时直接提示用户选择；随后选择 `selectedTargets` 或近期目标作为目标列表，重置暂停状态，写入空 `TransferSummary` 并进入结果页，再调用 `app.controller.startTransfer(files, targets, progress)`；进度回调和最终完成回调都通过 `Platform.runLater(...)` 调用 `showTransferProgress(...)`，最终完成或异常时清理 `transferRunning/transferPaused`。`togglePause()` 翻转 `app.transferPaused` 并调用 `app.controller.pauseTransfer(...)`。`showTransferResultPage()`、`resultSummarySection(...)` 和 `transferLogSection(...)` 根据汇总对象展示统计和日志。

## `src/main/java/com/iwmei/lantransfer/view/UserList.java`

所属功能：用户列表页面。

详细功能：展示全部可传输用户，提供搜索框、扫描入口、列表/矩阵视图切换、分页、添加近期传输对象和本地传输分组创建能力。当前设备数据来自 `LocalBackend.loadAllDevices()`：只显示 `LanPeer` 已知的本机和真实发现设备，不再用演示设备填充空页面。搜索框会按昵称、设备名、设备 ID 和目标地址实时过滤当前已加载设备。用户可以先用卡片上的 `+` 把多个用户选中，再填写分组名并点击“选中建组”，生成的组目标会加入近期传输对象。

实现方法：`showUserListPage()` 调用 `loadAllDevices()` 后在 UI 线程组装页面。搜索框文本保存在 `query` 字段中，`textProperty()` 变化时把页码重置为 0 并调用 `renderResults(...)`。分组名保存在 `groupName` 字段中，`saveSelectedGroup(String)` 会从 `app.selectedTargets` 中过滤掉已有组目标，并且只保存带真实 host/port 的可达用户，避免把不可达对象建成可发送分组；没有选中成员时 toast 提示，保存成功后调用 `app.addRecentTarget(group)` 把组放入近期传输对象并选中。`renderResults(...)` 使用 `DeviceSearch.matches(...)` 过滤设备，更新总数标签，再按当前视图模式渲染列表或矩阵。列表视图逐个调用 `app.userCard(device, true)`，矩阵视图由 `userGrid(...)` 按 15 个一页分页。点击用户卡中的添加按钮会通过 `MainWindow.addRecentTarget(...)` 维护近期和选中目标。

## `src/main/java/com/iwmei/lantransfer/view/Scan.java`

所属功能：局域网扫描页面。

详细功能：显示局域网扫描的进行中状态和完成结果。进入页面时立即展示“正在扫描局域网用户...”和取消按钮，后台扫描完成后展示“扫描完成”、发现用户数量、雷达图、隐身用户提示、“查看用户列表”和“重新扫描”按钮。当前扫描结果来自 `LocalBackend.scanLanDevices()`，后端会实际发 UDP 广播查找同样运行本程序的主机；扫描完成后用户可以跳回用户列表查看最新在线状态，也可以直接再次扫描。

实现方法：`showScanPage()` 先递增 `scanRunId` 生成本次扫描轮次，然后调用 `renderScanningPage(runId)` 立即渲染等待状态，避免后端扫描期间界面无响应；随后调用 `app.controller.scanLanDevices()` 启动真实扫描。扫描异常时通过 `exceptionally(error -> List.of())` 回退为空列表，保证页面仍能进入完成态。扫描返回后切回 JavaFX 线程，如果回调中的 `runId` 已不是当前 `scanRunId`，说明用户已经取消或重新发起扫描，旧回调直接丢弃，避免页面被过期扫描结果覆盖。`renderScanningPage(int)` 创建统一扫描布局、雷达空状态、转圈提示和取消按钮；取消按钮会让当前轮次失效并回到用户列表。`renderCompletedPage(List<UserDevice>)` 用 `app.radar(devices)` 生成完成雷达图，展示发现数量，并提供“查看用户列表”和“重新扫描”两个出口。`scanPage()` 统一创建 `.scan-page` 样式的居中基础容器。扫描页本身不关心 UDP 细节，只消费 `List<UserDevice>`；广播地址、端口、协议解析和本机响应都在 `LanPeer` 中实现。

## `src/main/java/com/iwmei/lantransfer/view/Mine.java`

所属功能：我的资料页面。

详细功能：展示当前用户资料、状态设置、自定义状态输入和账号更多信息。未登录时会回到登录页。昵称、设备名称和个性签名行点击“编辑”后可以修改，底部“保存”会把新的 `Profile` 写回 `acco` 账号表。状态卡可以直接点击切换，当前状态会高亮；自定义状态保存会更新账号状态，并把 `app.profile.signature()` 替换成输入文本后重绘页面。

实现方法：`showProfilePage()` 检查 `app.profile`，把 `Profile.status()` 写入 `selectedStatus`，再组装资料、状态和更多信息三个分区，并给保存/重置按钮绑定动作。`profileEditor(Profile)` 用头像和表格行展示资料，`editableRow(...)` 创建默认只读的 `TextField` 和“编辑”按钮，点击后允许输入。保存按钮调用 `readProfile()` 从输入框构造新的 `Profile`，再调用 `app.controller.updateProfile(...)` 持久化。`statusCards()` 展示五种 `UserStatus` 对应状态文案，`statusCard(...)` 绑定点击事件并调用 `saveStatus(...)`；`saveStatus(...)` 同步调用 `updateStatus(...)`、更新 `app.profile` 并刷新页面。`customStatusField()` 会把当前签名预填到输入框，保存时复用当前 `selectedStatus`。

## `src/main/java/com/iwmei/lantransfer/view/Settings.java`

所属功能：系统设置页面。

详细功能：展示本机局域网 IP、上传/下载限速、失败重试次数、主题色、字体、缩放、接收目录、传输口令、语言和启动设置。当前页面会从后端加载 `SystemSettings`，保存按钮会把可编辑设置写回本地设置文件；传输口令用于局域网发现小群组过滤，字体、字号和缩放会更新 `MainWindow.currentSettings` 并在页面重绘时应用到全局根节点样式。

实现方法：`showSettingsPage()` 调用 `app.controller.loadSettings()`，异步返回后在 JavaFX 线程执行 `render(SystemSettings)`。`render(...)` 先更新 `app.currentSettings` 和 `app.accentColor`，再根据设置值创建各控件，并把上传限速、下载限速、重试次数、主题色、字体、字号、缩放、接收目录、传输口令、语言和启动选项控件保存到字段，便于保存时读取。`receiveDirControls(...)` 使用 `DirectoryChooser` 选择目录，`groupControls(...)` 读写传输口令文本框。`colorControls(...)` 点击预设色会用新主题色重绘页面；`saveControls(...)` 读取控件生成新的 `SystemSettings`，调用 `app.controller.updateSettings(...)` 保存，并再次渲染让主题色、字体、缩放和传输口令立即生效。

## `src/main/java/com/iwmei/lantransfer/view/MainWindow.java`

所属功能：JavaFX 主窗口、路由和共享 UI 组件库。

详细功能：该类继承 `Application`，管理窗口尺寸、标题栏、认证窗口壳、主窗口壳、侧边栏、顶部栏、底部状态栏、页面路由、共享状态和大量复用控件。它持有 `AppController`、当前用户资料、当前系统设置、待传输文件、近期目标、选中目标、当前传输汇总、主题色、发送运行/暂停状态和用户列表分页状态，并负责在本机忙碌状态收到文件请求时弹出接收确认框，以及用 toast 展示接收端进度节点。

实现方法：`start(Stage)` 初始化透明窗口后先调用 `controller.setRxAsk(this::confirmReceive)` 和 `controller.setRxProgress(this::showRxProgress)`，把接收前确认和接收进度展示回调交给后端，再异步读取系统设置，拿到 `accentColor` 和 `currentSettings` 后进入登录页；读取失败时仍进入登录页。`confirmReceive(...)` 是给 `UdpRx` 后台线程调用的同步确认方法，如果已经在 JavaFX 线程就直接显示确认框，否则用 `Platform.runLater(...)` 切回 UI 线程并用 `CompletableFuture` 等待最多 30 秒，超时或异常按拒收处理。`showReceiveConfirm(...)` 构造 JavaFX `Alert` 确认框，显示文件名和大小，用户点 OK 才返回允许。`showRxProgress(...)` 接收文件名和百分比，如果当前已经在 JavaFX 线程就直接 toast，否则用 `Platform.runLater(...)` 切回 UI 线程显示“接收 文件名 百分比%”。`showAuth(...)`、`showFileTransferPage()`、`showUserListPage()`、`showScanPage()`、`showProfilePage()`、`showSettingsPage()` 等方法只做页面路由转发。`setAuthPage(...)` 和 `setMainPage(...)` 把页面内容放入统一窗口壳，`setWindow(...)` 负责 Scene、CSS、尺寸和首次显示。`appRoot(...)` 调用 `rootStyle(...)` 把主题色、字体、字号和缩放倍率写入根节点 CSS；缩放用 `fontSize * zoomPercent / 100` 计算全局字号，避免直接缩放窗口导致裁剪。`windowShell(...)`、`mainWindowShell(...)`、`titleBar()`、`sidebar(...)`、`mainTopbar()` 和 `statusFooter()` 组成应用外框；`statusFooter()` 会显示 `currentSettings.receiveDir()`，更改按钮跳转设置页。`cardGrid(...)`、`addCard(...)`、`userCard(...)`、`addRecentTarget(...)`、`tableGrid(...)`、`addTransferRow(...)` 等提供业务页面共享布局；组目标也复用 `userCard(...)`，因此近期对象不需要单独 UI 类型。`transferRunning` 和 `transferPaused` 是文件传输页的共享运行状态，防止重复提交并控制暂停按钮文案。`titleLabel(...)`、`mutedLabel(...)`、`accentLabel(...)`、`textField(...)`、`passwordField(...)`、`primaryButton(...)`、`secondaryButton(...)`、`outlineButton(...)`、`ghostTextButton(...)`、`compactButton(...)`、`iconToggleButton(...)` 和 `textButton(...)` 统一控件样式。`radar(...)`、`scanDeviceLabel(...)`、`statusLine(...)`、`avatar(...)`、`statCard(...)`、`statusCard(...)`、`progressCell(...)`、`operationCell(...)`、`statusBadge(...)` 和 `logLine(...)` 构造具体视觉组件。`copyToClipboard(...)` 和 `toast(...)` 处理用户反馈。
