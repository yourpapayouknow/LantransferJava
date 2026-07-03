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

## Local Tooling

- Java/Maven 等本机通用开发工具优先安装到 `D:\Programs\Java_UniversalLanguage\`，不要安装到 `C:\Users\...`，除非用户明确要求。

## Project Layout

- `src/main/java/com/iwmei/lanudp/app/`：应用启动入口，只负责启动 JavaFX。
- `src/main/java/com/iwmei/lanudp/ui/fx/`：JavaFX 前端界面代码。
- `src/main/resources/com/iwmei/lanudp/ui/fx/`：JavaFX 前端样式资源。
- `src/main/java/com/iwmei/lanudp/backend/`：后端接口和真实后端实现。
- `src/main/java/com/iwmei/lanudp/backend/mock/`：前端联调用假数据实现。
- `MockBackendFacade` 只给前端联调使用；真实后端写好后替换它。

## Frontend Scope

- 不恢复 Swing 旧前端；除非用户明确要求，`javax.swing` / `java.awt` 前端代码不应重新出现。
- 本线程后续仅修改这些前端相关路径：
  - `src/main/java/com/iwmei/lanudp/ui/fx/`
  - `src/main/resources/com/iwmei/lanudp/ui/fx/`
  - `src/main/java/com/iwmei/lanudp/app/LanUdpFileDistributorApp.java`（仅启动入口）
  - `pom.xml`（仅构建/运行 JavaFX 需要时）
- `src/main/java/com/iwmei/lanudp/backend/` 不是前端目录；前端线程只在接口联调需要时改 `BackendFacade` / `backend/mock/MockBackendFacade`。
