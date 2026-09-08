# OryxOS 部署指南（从零到首对话 ≤ 30 分钟）

> 对应 SC-001 可运维性验收。单可执行 JAR 单二进制部署（research R-11），
> 私有部署，数据不出域——全部数据落在工作区 `.oryxos/` 下。

## 1. 前置条件（约 5 分钟）

| 项 | 要求 | 检查命令 |
|---|---|---|
| JDK | 21+（运行 JAR 即可，无需 Maven） | `java -version` |
| LLM 密钥 | 至少一家（DeepSeek / Kimi / 通义），**仅注入环境变量** | `echo $DEEPSEEK_API_KEY` |
| 磁盘 | ≥ 500MB（JAR 90MB + 工作区 + SQLite） | — |

获取 JAR（二选一）：
- 发布包：`release/oryxos-boot-<version>.jar`（`scripts/package.sh` 产物）
- 自行构建：`mvn -pl oryxos-boot -am package -DskipTests`

## 2. 三步上线（约 10 分钟）

```bash
# ① 建议用 bin/oryxos 包装脚本（自动定位 JAR）；也可直接 java -jar
bin/oryxos init                          # 生成 .oryxos/ 工作区，幂等
export DEEPSEEK_API_KEY=sk-xxxx          # 密钥只走环境变量，绝不写文件
bin/oryxos profile create hello          # 生成 Agent 模板
```

编辑 `.oryxos/agents/hello/AGENT.md`（唯一必改处）：

```yaml
provider:
  name: deepseek        # 与 config/application.yml 的 oryxos.providers 对应
  model: deepseek-chat
```

## 3. 首对话验证（约 5 分钟）

```bash
bin/oryxos chat --profile hello
> 你好，介绍一下你自己      # 流利答复即通；/exit 退出
bin/oryxos status          # Agent=1、Provider 密钥 ✓、Database ok
bin/oryxos session list    # 可见刚才的会话
```

## 4. 服务化运行（约 5 分钟）

```bash
# 前台 REST 服务
bin/oryxos serve --port 8080
curl -s localhost:8080/api/v1/health       # {"success":true,...}
# API 文档：http://localhost:8080/swagger-ui/index.html

# 或常驻守护进程（REST + 定时任务同进程）
bin/oryxos gateway
```

systemd 托管示例：

```ini
[Unit]
Description=OryxOS Gateway
After=network.target

[Service]
Environment=DEEPSEEK_API_KEY=sk-xxxx
Environment=ORYXOS_ROOT=/opt/oryxos/.oryxos
ExecStart=/usr/bin/java -jar /opt/oryxos/oryxos-boot-1.0.0.jar gateway
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

## 5. 日常运维

| 操作 | 命令 |
|---|---|
| 健康检查 | `bin/oryxos status`（工作区/Agent/Provider/会话/DB/调度器） |
| 会话排查 | `bin/oryxos session list [--profile <name>]` |
| 密钥状态 | `bin/oryxos provider list`（只显变量名与 ✓/✗，不回显明文） |
| 审计追溯 | `sqlite3 .oryxos/oryxos.db "select * from tool_invocations order by id desc limit 10;"` |
| 数据备份 | 停进程后打包整个 `.oryxos/` 目录（库+记忆+Agent 定义一体） |
| 改配置生效 | `AGENT.md`/白名单/Provider 均下次启动或新会话生效（无热加载，FR-028） |

## 6. 安全要点

1. **密钥**：只走环境变量（systemd `Environment=` 或 shell export）；配置文件里
   只有 `api-key-env` 变量名。泄露处置 = 轮换密钥，本机无密钥残留。
2. **沙箱白名单**：`config/application.yml` 的 `oryxos.sandbox` 段按需收紧
   （文件路径/命令/HTTP 域名三张白名单），首启种子进 SQLite。
3. **网络**：默认监听 0.0.0.0:8080，内网部署建议用防火墙或反向代理收口。

## 7. 故障排查

| 症状 | 首查 |
|---|---|
| 启动报 Provider 配置错误 | 报错逐条点名了字段与修复指引（FR-008 聚合校验），照改即可 |
| 报密钥未设置 | `provider list` 看哪个 env 是 ✗；export 后重启 |
| chat 报 Agent 不存在 | `profile list` + 运行目录是否对（`status` 看 Workspace 路径） |
| session 查不到 | 库在 `ORYXOS_ROOT/oryxos.db`；回当初启动 serve 的目录/环境变量 |
| 定时任务未触发 | `status` 的 Scheduler 行；cron 表达式；schedules 是否在 frontmatter |

完整验收场景见 [quickstart](../specs/001-agent-os-core/quickstart.md)；
压测与性能验收见 [scripts/load-test.md](../scripts/load-test.md)。
