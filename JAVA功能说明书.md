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

- 暂无。遇到无法实现或不合理功能时，在这里新增条目，写清功能、原因、当前占位和推荐替代方案。

## `src/main/java/com/iwmei/lantransfer/App.java`

所属功能：应用启动入口。

详细功能：`App` 只负责把 JVM `main` 方法交给 JavaFX，启动 `MainWindow`。它不保存业务状态，不创建服务对象，也不参与页面路由。

实现方法：`main(String[] args)` 调用 `Application.launch(MainWindow.class, args)`，由 JavaFX 生命周期继续执行 `MainWindow.start(Stage)`。这个文件保持极薄入口，后续不要把登录、网络、传输初始化塞进这里；需要启动时初始化时，优先放到控制器或后端服务里。

## `src/main/java/com/iwmei/lantransfer/controller/AppController.java`

所属功能：界面和业务服务之间的控制层。

详细功能：`AppController` 持有一个 `BackendFacade` 实例，给 JavaFX 页面提供登录、注册、近期设备、全部设备、扫描设备和启动传输的统一入口。当前实现实例化 `LocalBackend`，登录注册走本地真实账号仓库，其余暂未落地功能由本地后端临时复用演示数据。

实现方法：每个公开方法都只做转发，例如 `login(LoginRequest)` 返回 `backend.login(request)` 的 `CompletableFuture<AuthResult>`。这种薄控制器保证页面层不直接知道账号文件、UDP 传输或扫描实现。后续新增资料保存、状态保存、设置保存时，应先在 `BackendFacade` 补接口，再由这里转发。

## `src/main/java/com/iwmei/lantransfer/service/BackendFacade.java`

所属功能：前后端交互接口。

详细功能：该接口定义 UI 当前需要的业务能力，包括登录、注册、加载近期传输对象、加载全部设备、扫描局域网设备、启动传输、更新资料、更新状态和更新设置。它是页面层和真实业务实现之间的边界。

实现方法：查询和长耗时操作返回 `CompletableFuture`，便于 JavaFX 页面在完成后用 `Platform.runLater` 回到 UI 线程；纯写入接口目前是同步 `void`，后续如果写文件或网络操作变慢，应改成 `CompletableFuture<Void>`。新增后端功能时先判断 UI 是否真的需要接口，避免提前铺接口。

## `src/main/java/com/iwmei/lantransfer/service/MockBackendFacade.java`

所属功能：前端联调用假数据后端。

详细功能：当前类内置一个 `Profile` 和 18 个 `UserDevice`，支持演示登录、注册、近期设备、全部设备、扫描设备和传输结果。登录只接受 `admin/admin`，注册总是返回“注册申请已提交”，传输总是返回固定的 4 条任务和 5 条日志。

实现方法：所有方法都用 `CompletableFuture.completedFuture(...)` 立即返回，模拟异步后端但不做真实 IO。`loadRecentDevices()` 返回前 5 个设备，`scanLanDevices()` 返回前 4 个设备，`startTransfer(...)` 会在未选择目标时使用前 5 个设备作为兜底目标，并根据目标数量构造 `TransferSummary`。后续真实后端完成后，该类只保留给演示或测试，不再作为主实现。

## `src/main/java/com/iwmei/lantransfer/service/AuthStore.java`

所属功能：无服务器登录注册账号仓库。

详细功能：`AuthStore` 负责第一屏登录与注册的真实后端逻辑。它在用户目录下创建 `.lantransfer/<仓库名>/users.properties`，以当前 GitHub 远程仓库名作为本地账号命名空间，避免把账号数据提交进项目仓库。它支持默认 `admin/admin` 账号、新账号注册、重复账号拦截、账号格式校验、密码 PBKDF2 摘要、登录密码校验、最后登录时间更新和 `Profile` 构造。

实现方法：`login(LoginRequest)` 先清洗账号并校验空输入，再加载账号文件并确保默认管理员存在；账号不存在时返回失败，密码摘要不匹配时返回失败，匹配时更新 `lastLoginAt` 并返回包含资料的 `AuthResult`。`register(RegisterRequest)` 校验账号、密码和重复账号，生成盐和密码摘要，写入用户 ID、昵称、设备名、签名、注册时间、最后登录时间和语言。账号文件用 Java `Properties` 读写，密码用 `PBKDF2WithHmacSHA256` 和 120000 次迭代存摘要，不保存明文。`repoOrigin()` 读取 `.git/config` 中的 origin 地址，`repoSlug()` 提取仓库名作为存储目录；没有 Git 仓库时退回 `LantransferJava`。该实现是本地替代方案，不依赖服务器和 GitHub token。

## `src/main/java/com/iwmei/lantransfer/service/LocalBackend.java`

所属功能：当前主后端组合实现。

详细功能：`LocalBackend` 是 `AppController` 当前使用的真实后端入口。它把登录和注册交给 `AuthStore`，把尚未实现的设备列表、扫描、传输、资料、状态和设置功能临时委托给 `MockBackendFacade`，保证 App 在逐步替换后端时仍可运行。

实现方法：`login(...)` 和 `register(...)` 使用 `CompletableFuture.supplyAsync(...)` 执行账号文件 IO，避免阻塞 JavaFX 事件线程。其余方法直接调用 `demo` 对象。后续每完成一个大功能，就把对应方法从 `demo.xxx(...)` 替换为真实实现，并同步更新本文档。

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

详细功能：描述本机 IPv4/IPv6、上传限速、下载限速、最大重试次数、主题色、字体、字号和缩放比例。

实现方法：使用 `record` 汇总设置页需要保存的数据。当前设置页还未真正读写此对象，后续应由服务层提供加载和保存方法，UI 只负责把控件值组装成 `SystemSettings`。

## `src/main/java/com/iwmei/lantransfer/model/UserDevice.java`

所属功能：局域网用户设备数据对象。

详细功能：表示一个可传输目标，包含账号或设备 ID、昵称、设备名、在线状态、上次在线时间、头像文字、头像颜色和是否使用图片头像。

实现方法：使用 `record` 让设备列表、近期对象、扫描雷达和传输任务共享同一数据结构。`DeviceStatus` 决定在线/离线样式，`lastSeen` 当前是展示文本，后续真实在线检测可改为由服务层生成。

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

详细功能：验证第一屏后端最关键路径：默认管理员能登录，新账号能注册，本地注册不进入审核等待，重复注册失败，错误密码失败，正确密码登录成功并返回资料。

实现方法：`main(String[] args)` 创建临时目录，把 `AuthStore` 指向临时 `users.properties`，依次调用注册和登录接口，用 `require(...)` 抛出 `AssertionError` 表示失败，最后删除临时目录。运行方式是先编译测试类，再执行 `java -cp target/classes;target/test-classes com.iwmei.lantransfer.service.AuthStoreCheck`。

## `src/main/java/com/iwmei/lantransfer/view/FileTransfer.java`

所属功能：文件传输首页、待发送列表和传输结果页。

详细功能：加载近期传输对象，显示上传文件/文件夹入口，支持拖拽加入待传输项，展示近期目标、传输列表、传输结果统计和传输日志。它也是从登录后进入的第一个主功能页。

实现方法：`showFileTransferPage()` 调用控制器加载近期设备，首次进入时填充 `app.recentTargets`。`uploadStrip()` 绑定文件选择、文件夹选择、拖拽进入/离开/释放事件，并按 `app.pendingFiles` 动态显示开始发送和清除按钮。`pendingFileCard(...)` 使用 `FileIcons` 展示图标、大小和修改时间。`startTransfer()` 校验待传输项，选择 `selectedTargets` 或近期目标作为目标列表，再调用后端返回 `TransferSummary` 并跳转结果页。`showTransferResultPage()`、`resultSummarySection(...)` 和 `transferLogSection(...)` 根据汇总对象展示统计和日志。

## `src/main/java/com/iwmei/lantransfer/view/UserList.java`

所属功能：用户列表页面。

详细功能：展示全部可传输用户，提供搜索框、扫描入口、列表/矩阵视图切换、分页和添加近期传输对象能力。

实现方法：`showUserListPage()` 调用 `loadAllDevices()` 后在 UI 线程组装页面。列表视图逐个调用 `app.userCard(device, true)`，矩阵视图由 `userGrid(...)` 按 15 个一页分页。点击用户卡中的添加按钮会通过 `MainWindow.addRecentTarget(...)` 维护近期和选中目标。

## `src/main/java/com/iwmei/lantransfer/view/Scan.java`

所属功能：局域网扫描页面。

详细功能：显示扫描中状态、雷达图、扫描到的设备标签、隐身用户提示和取消扫描按钮。

实现方法：`showScanPage()` 调用 `scanLanDevices()` 获取设备列表，然后用 `app.radar(devices)` 生成雷达布局。取消按钮回到用户列表。当前后端立即返回假设备，后续真实扫描应在服务层实现广播/组播发现并保持同一返回类型。

## `src/main/java/com/iwmei/lantransfer/view/Mine.java`

所属功能：我的资料页面。

详细功能：展示当前用户资料、状态设置、自定义状态输入和账号更多信息。未登录时会回到登录页。

实现方法：`showProfilePage()` 检查 `app.profile`，再组装资料、状态和更多信息三个分区。`profileEditor(Profile)` 用头像和表格行展示昵称、用户 ID、设备名称和个性签名。`statusCards()` 展示五种 `UserStatus` 对应状态文案。`customStatusField()` 当前只提示接口预留，后续应调用控制器保存状态。

## `src/main/java/com/iwmei/lantransfer/view/Settings.java`

所属功能：系统设置页面。

详细功能：展示本机局域网 IP、上传/下载限速、失败重试次数、主题色、字体、缩放、语言和启动设置。

实现方法：`showSettingsPage()` 统一把每个设置项交给 `settingsRow(...)` 包装。`ipInfo()` 当前显示固定 IP 并复用复制按钮。`speedLimitControls()`、`retryControls()`、`colorControls()`、`fontControls()`、`zoomControls()`、`languageControls()` 和 `startupControls()` 分别构造对应控件。主题色点击后直接改 `app.accentColor` 并重绘页面；其余控件后续接入 `SystemSettings` 保存。

## `src/main/java/com/iwmei/lantransfer/view/MainWindow.java`

所属功能：JavaFX 主窗口、路由和共享 UI 组件库。

详细功能：该类继承 `Application`，管理窗口尺寸、标题栏、认证窗口壳、主窗口壳、侧边栏、顶部栏、底部状态栏、页面路由、共享状态和大量复用控件。它持有 `AppController`、当前用户资料、待传输文件、近期目标、选中目标、当前传输汇总、主题色和用户列表分页状态。

实现方法：`start(Stage)` 初始化透明窗口并进入登录页。`showAuth(...)`、`showFileTransferPage()`、`showUserListPage()`、`showScanPage()`、`showProfilePage()`、`showSettingsPage()` 等方法只做页面路由转发。`setAuthPage(...)` 和 `setMainPage(...)` 把页面内容放入统一窗口壳，`setWindow(...)` 负责 Scene、CSS、尺寸和首次显示。`windowShell(...)`、`mainWindowShell(...)`、`titleBar()`、`sidebar(...)`、`mainTopbar()` 和 `statusFooter()` 组成应用外框。`cardGrid(...)`、`addCard(...)`、`userCard(...)`、`addRecentTarget(...)`、`tableGrid(...)`、`addTransferRow(...)` 等提供业务页面共享布局。`titleLabel(...)`、`mutedLabel(...)`、`accentLabel(...)`、`textField(...)`、`passwordField(...)`、`primaryButton(...)`、`secondaryButton(...)`、`outlineButton(...)`、`ghostTextButton(...)`、`compactButton(...)`、`iconToggleButton(...)` 和 `textButton(...)` 统一控件样式。`radar(...)`、`scanDeviceLabel(...)`、`statusLine(...)`、`avatar(...)`、`statCard(...)`、`statusCard(...)`、`progressCell(...)`、`operationCell(...)`、`statusBadge(...)` 和 `logLine(...)` 构造具体视觉组件。`copyToClipboard(...)` 和 `toast(...)` 处理用户反馈。后续后端接入时，避免把业务逻辑继续塞进这个类，只让它保留页面状态和控件构建职责。
