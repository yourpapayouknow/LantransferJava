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

## Frontend Scope

- 当前前端只保留 JavaFX：`src/main/java/com/zjh/lanudp/ui/fx` 与 `src/main/resources/com/zjh/lanudp/ui/fx/app.css`。
- 不恢复 Swing 旧前端；除非用户明确要求，`javax.swing` / `java.awt` 前端代码不应重新出现。
