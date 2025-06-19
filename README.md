# CTAntiDdos

> 一个为 Minecraft Paper 1.20.4+ 服务器设计的多层次智能防护插件，专注于抵御DDoS、Bot、恶意刷包等攻击。由 CodeTea(ZMF) 团队开发。

---

## 项目简介

CTAntiDdos 是一款高性能、可配置、支持多语言的 Paper 服务器防护插件，集成了IP限流、Bot检测、智能行为分析、动态防护、日志归档、自动更新检测等多项功能，助力服务器安全稳定运行。

---

## 主要功能
- **IP连接频率/数量限制**：防止同一IP刷连接。
- **Bot攻击检测与拦截**：识别并拦截短时间大量假人进服。
- **智能Bot识别**：基于玩家行为（移动、聊天、交互）自动识别假人。
- **自动封禁/踢出可疑IP**：可临时封禁恶意IP。
- **动态防护策略**：根据TPS、在线人数、攻击频率自动调整防护强度。
- **日志记录与轮转**：详细记录防护事件，自动归档旧日志。
- **多语言支持**：内置中、英、日、韩等多语言，支持自定义扩展。
- **指令与热重载**：支持热重载、状态查询、手动解封、帮助等管理指令。
- **自动检测兼容性与更新**：启动时检测环境与新版本，OP自动提醒。

---

## 安装与部署

1. **编译/下载插件**
   - 使用 `mvn clean package` 编译，或前往 [GitHub Releases](https://github.com/ZMF-1/CTAntiDdos/releases) 下载已发布的 `.jar` 文件。
2. **放入服务器**
   - 将 `CTAntiDdos-*.jar` 放入 Paper 服务器的 `plugins/` 目录。
3. **启动/重启服务器**
   - 插件会自动生成默认配置文件和日志目录。
4. **（可选）配置自定义**
   - 修改 `plugins/CTAntiDdos/config.yml`，根据需求调整参数和语言。
5. **热重载配置**
   - 使用 `/ctad reload` 指令，无需重启服务器。

---

## 配置详细教程

1. **基础配置**
   - `lang`: 设置插件消息语言（如 zh、en、ja、ko、zh_tw）。
   - `ip-limit`: 配置IP限流功能，包括最大连接数、时间间隔等。
   - `bot-detection`: 配置Bot攻击检测的阈值和时间窗口。
   - `auto-ban`: 配置自动封禁功能，包括封禁时长。
   - `whitelist.ips`: 添加信任IP，免受限流和封禁。
   - `log`: 配置日志记录、格式、轮转大小和归档数量。
   - `dynamic-protection`: 配置动态防护策略，包括TPS、在线人数、攻击频率阈值及各模式参数。
   - `smart-bot-detection`: 配置智能Bot识别（如是否要求移动、聊天、交互等）。
   - `external-firewall`: 配置外部API或命令联动（如云防火墙、自动拉黑IP）。

2. **多语言消息自定义**
   - 在 `messages` 节点下，按语言分组自定义所有提示消息。
   - 支持 zh、zh_tw、en、ja、ko 等，格式如下：
     ```yaml
     messages:
       zh:
         ip-limit: "§c[CTAntiDdos] 连接过于频繁，请稍后再试。"
         ...
       en:
         ip-limit: "§c[CTAntiDdos] Too many connections, please try again later."
         ...
     ```

3. **日志归档与轮转**
   - `log.max-size`: 单个日志文件最大字节数，超出后自动归档。
   - `log.max-archives`: 最多保留多少份归档日志。

4. **动态防护参数**
   - `dynamic-protection.enabled`: 是否启用动态防护。
   - `dynamic-protection.tps-thresholds`: 不同防护等级的TPS阈值。
   - `dynamic-protection.player-thresholds`: 不同防护等级的在线人数阈值。
   - `dynamic-protection.attack-rate-thresholds`: 不同防护等级的攻击频率阈值。
   - `dynamic-protection.ip-limit`/`bot-threshold`: 各等级下的限流和Bot检测参数。

5. **外部API联动**
   - `external-firewall.enabled`: 是否启用外部API。
   - `external-firewall.api-url`: 调用API拉黑IP（如云防火墙）。
   - `external-firewall.command`: 本地命令联动（如iptables等）。

6. **白名单配置**
   - 在 `whitelist.ips` 下添加信任IP，格式如：
     ```yaml
     whitelist:
       ips:
         - "127.0.0.1"
         - "::1"
     ```

---

## 指令说明

| 指令                | 权限         | 说明                       |
|---------------------|--------------|----------------------------|
| `/ctad help`        | ctad.admin   | 显示帮助与指令说明         |
| `/ctad reload`      | ctad.admin   | 热重载配置                 |
| `/ctad status`      | ctad.admin   | 查看详细防护状态           |
| `/ctad unban <ip>`  | ctad.admin   | 手动解封指定IP             |
| `/ctad update`      | ctad.admin   | 检查插件新版本             |

---

## 自动更新说明

- 插件启动时会自动检查 [GitHub Releases](https://github.com/ZMF-1/CTAntiDdos/releases) 上的最新版本。
- 管理员可通过 `/ctad update` 指令手动检查新版本。
- 如有新版本，OP玩家上线时会收到提醒。
- 自动更新API地址已配置为：
  `https://api.github.com/repos/ZMF-1/CTAntiDdos/releases/latest`
- 如需手动下载，请访问 [Releases页面](https://github.com/ZMF-1/CTAntiDdos/releases)。

---

## 多语言支持

- 支持简体中文（zh）、繁体中文（zh_tw）、英文（en）、日语（ja）、韩语（ko）等。
- 在 `config.yml` 设置 `lang: zh` 等即可切换。
- 所有消息均可自定义，支持扩展更多语言。

---

## 常见问题

**Q: 插件未生效/报错？**  
A: 检查 `plugin.yml`、`config.yml` 配置，确认服务器为 Paper 1.20.4+ 且 Java 17+，查看控制台日志。

**Q: 日志乱码？**  
A: 请确保所有源文件和配置文件均为 UTF-8 编码，并在 `pom.xml` 设置 `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>`。

**Q: 如何扩展/自定义防护？**  
A: 参考 `config.yml` 注释，或联系 CodeTea(ZMF) 团队获取支持。

**Q: 如何升级插件？**  
A: 替换 `.jar` 文件，合并新旧配置，重启服务器。

---

## 贡献方式

欢迎提交 Issue、Pull Request 或参与文档完善！

1. Fork 本仓库，提交你的改动。
2. 保持代码风格一致，建议使用 UTF-8 编码。
3. 提交前请测试功能。

---

## 许可证

本项目采用 GPLv3 License 开源。

---

## 联系我们

- 团队：CodeTea(ZMF)
- GitHub: [https://github.com/ZMF-1/CTAntiDdos](https://github.com/ZMF-1/CTAntiDdos)
- 邮箱：admin@1427.top 或 zzmf20110806@163.com