<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- Use `codegraph_explore` for area understanding and `codegraph_node` for a specific file or symbol.
- If CodeGraph has no match because files were moved or deleted, fall back to filesystem inspection immediately.
<!-- CODEGRAPH_END -->

## Git Safety

- 一改动一提交：每完成一个独立改动，必须立即编译/验证并创建一次 git commit。
- 提交只包含本次改动相关文件；不要把无关文档、IDE 配置或其他线程留下的改动顺手带入提交。
- 删除代码前先确认当前 JavaFX 前端核心文件已经提交备份。

## Rule Capture

- 用户提出新的长期项目规则、开发约束或流程要求时，默认同步写入根目录 `AGENTS.md`；只有明确说明仅本次临时适用时才不写入。
- 每次开始新任务或恢复任务前，先核对根目录 `AGENTS.md` 中的项目规则，再决定实施步骤；如果发现当前执行方式和规则冲突，按 `AGENTS.md` 修正。

## Local Tooling

- Java/Maven 等本机通用开发工具优先安装到 `D:\Programs\Java_UniversalLanguage\`，不要安装到 `C:\Users\...`，除非用户明确要求。

## Java Comment Rule

- 所有非占位 Java 类、接口、枚举、record 的类型声明前必须有一行 `//` 注释说明职责。
- 所有显式方法、构造器、接口方法声明前必须有一行 `//` 注释说明该方法负责什么。
- 注释放在方法头或类型头的上一行；如果方法有 `@Override` 等注解，注释放在注解上一行。

## Project Layout

- `src/main/java/com/iwmei/lantransfer/App.java`：应用启动入口，只负责启动 JavaFX。
- `src/main/java/com/iwmei/lantransfer/view/`：JavaFX 界面页面、组件、窗口。
- `src/main/java/com/iwmei/lantransfer/controller/`：连接界面和业务逻辑，处理页面事件。
- `src/main/java/com/iwmei/lantransfer/model/`：用户、设备、传输任务等数据对象。
- `src/main/java/com/iwmei/lantransfer/service/`：登录、扫描、传输等业务逻辑。
- `src/main/java/com/iwmei/lantransfer/util/`：文件、时间、网络等通用工具。
- `src/main/resources/css/`：CSS 样式。
- `src/main/resources/images/`：图片资源。
- `src/main/resources/icons/`：图标资源。
- `MockBackendFacade` 只给前端联调使用；真实后端写好后替换它。
- 所有新增文件和目录命名都必须从简，避免冗长完整英文名；能用短名表达清楚时用短名，例如账号表使用 `acco`，注册请求目录使用 `req`。

## Java Manual Rule

- 根目录必须维护 `JAVA功能说明书.md`，按照 Java 文件分类记录每个类在 App 中的所属功能、详细代码功能、尽可能详细的实现方法和维护注意事项。
- 每新增或修改一个 Java 类，都必须在同一轮改动中更新 `JAVA功能说明书.md` 的对应条目；同一个 Java 文件中的多个功能必须在该文件条目下逐项说明，不能遗漏。
- 如果某个功能因复杂、不可实现或不合理而暂不实现，必须在 `JAVA功能说明书.md` 的“功能跳过记录”中写明功能、原因、当前占位方式和推荐替代方案。

## Unimplemented Feature Ledger

- 根目录必须维护 `未实现功能清单0.md`，单独记录当前尚未覆盖、仍是占位、仅保存配置但没有业务闭环、因复杂度或环境原因跳过的功能。
- 每次发现漏实现功能、跳过功能、或用户要求列出未实现功能时，都必须同步更新 `未实现功能清单0.md`，写清功能名称、当前状态、未实现原因和推荐补法。
- 某个未实现功能补齐并通过验证后，必须从 `未实现功能清单0.md` 移除或改写为已完成说明，避免清单误导后续开发。

## Backend Implementation Rules

- 当前阶段开始明确编写后端功能；旧前端范围限制不再阻止修改 `model/`、`service/`、`util/`、`controller/` 和必要的新后端目录。
- 后端功能按 App UI 使用顺序实现：先登录/注册，再文件传输首页、用户列表、局域网扫描、我的资料、系统设置和传输结果相关能力。
- 核心功能以 `doc/实验报告.docx` 为准：局域网广播或组播发现、多目标选择、UDP 多线程文件发送、确认机制、失败三次重试、发送结果报告，以及登录注册、拖拽文件类型匹配、近期对象记录、在线检测、限速分配、分片缓存与断点重传、完整性校验、剩余时间统计、传输口令小群组、按用户状态条件传输。
- 第一页登录与注册没有服务器时，必须使用当前 GitHub 远程仓库能力做简单替代实现：根目录 `acco` 是账号表，`req/` 是注册请求目录，App 把注册请求直接 push 到仓库，GitHub Actions 自动把注册请求合入 `acco`；不得把本机用户目录账号文件作为主凭据库，不得引入重型服务端框架。
- 普通用户机器没有仓库拥有者凭据时，注册 push 只能使用运行时注入的辅助账号 token（环境变量 `ACCO_T` 或 JVM 参数 `-Dacco.t=...`）或本机已登录的 Git 凭据；不得把辅助账号密码、token 或其它密钥写入代码、文档、提交记录、说明书或配置文件。
- 可以用浏览器协助用户打开 GitHub token 创建页面，但 token 的创建、复制和保存必须由用户处理；Agent 不获取、不记录、不提交任何 GitHub 密钥。
- GitHub 注册不能通过打开浏览器网页伪装成 App 功能；App 运行时必须通过 Git push 或直接 HTTP 等真实发包路径完成请求。
- 每完成一个大功能再测试，避免每个小函数都单独跑测试；测试失败必须先修复或在说明书记录原因。
- 一旦某功能实现复杂度过高、当前环境无法实现或需求本身不合理，跳过实现并保留占位，记录原因和替代方案，然后继续后续功能。
- Java 文件命名从简，避免冗长完整英文名；类型和方法职责靠头部 `//` 注释说明。
- 每完成一个独立改动，必须验证并创建本地 git commit；提交只包含本次相关文件。

## Frontend Scope

- 不恢复 Swing 旧前端；除非用户明确要求，`javax.swing` / `java.awt` 前端代码不应重新出现。
- 前端改动仍只修改这些路径；后端阶段不受此列表限制：
  - `src/main/java/com/iwmei/lantransfer/view/`
  - `src/main/java/com/iwmei/lantransfer/controller/`
  - `src/main/resources/css/`
  - `src/main/java/com/iwmei/lantransfer/App.java`（仅启动入口）
  - `pom.xml`（仅构建/运行 JavaFX 需要时）
- `src/main/java/com/iwmei/lantransfer/service/` 不是前端目录；前端线程只在接口联调需要时改 `BackendFacade` / `MockBackendFacade`。
