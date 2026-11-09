# Villager Census / 村民普查

A client-only Fabric mod that batch-records villager professions, levels and trades, then exports a readable report — built for large or hand-made trading halls.

纯客户端 Fabric Mod：批量统计村民的职业、等级与交易，结束后导出可读报告，适合大型或手工搭建的交易大厅。

## 阅读信息 / Reading info

| 部分 / Section | 字数 / Length | 充分阅读时间 / Full read |
|----------------|---------------|--------------------------|
| [中文](#中文) | 约 2,000 字 | 约 6 分钟 / ~6 min |
| [English](#english) | 1,318 words | ~7 分钟 / ~7 min |
| [LICENSE](LICENSE) | 200 words | ~1 分钟 / ~1 min |

---

## 中文

### 这是什么

- **纯客户端**：只装在客户端，不需要服务端安装。
- 手持触发物品右键村民即可登记，交易界面会被拦截并自动记录交易。
- 结束后导出 TXT 报告（可选 JSON），方便筛选附魔书、装备与低价交易。
- 只读取游戏已有的客户端数据，不修改世界，也不额外向服务端发包。

### 支持版本

| Minecraft | Java | MaLiLib |
|-----------|------|---------|
| 1.21.11   | 21   | 0.27.20 |
| 26.3      | 25   | 0.30.0  |

### 依赖

- [Fabric Loader](https://fabricmc.net/) 0.19 及以上
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [MaLiLib（sakura-ryoko 分支）](https://masa.dy.fi/maven/sakura-ryoko/) — 配置界面、快捷键与 i18n
- 可选：[ModMenu](https://modrinth.com/mod/modmenu) — 从 Mod 列表打开配置

### 安装

1. 为对应的 Minecraft 版本安装 Fabric Loader。
2. 将 `fabric-api` 与对应版本的 `malilib` 放入 `mods/`。
3. 将 `villagercensus-<版本>+<mc>.jar` 放入 `mods/`。
4. 启动游戏，配置文件位于 `config/villagercensus.json`。

### 快速开始

1. 手持触发物品（默认：附魔书）。
2. 输入 `/census start 大厅A` 开始普查。
3. 逐个右键村民。交易界面会被拦截，职业与交易自动记录。
4. 输入 `/census stop` 结束并写出报告。

### 命令

| 命令 | 作用 |
|------|------|
| `/census start <名称>` | 在当前维度开始普查 |
| `/census fork <名称>` | 按会话名称 fork 已有半成品或报告并继续统计 |
| `/census stop` | 结束并写出报告 |
| `/census abort` | 放弃当前会话，不写报告 |
| `/census undo` | 撤销上一次记录 |
| `/census remark <文本>` | 给上一只村民添加备注 |
| `/census status` | 查看当前会话状态 |
| `/census resume` | 续接找到的半成品 |
| `/census discard` | 归档找到的半成品 |
| `/census pause` | 暂停或恢复统计 |
| `/census reverse` | 切换“已统计 / 未统计”发光 |
| `/census books` | 切换图书管理员附魔书竞争力标记 |
| `/census help` | 显示帮助 |

### 标记模式

普查进行中发光标记有三种模式，HUD 始终显示当前模式。附魔书模式会暂存“常规 / 反转”状态，关闭后恢复。

| 模式 | 发光对象 |
|------|----------|
| 常规 | 已统计的村民 |
| 反转 | 未统计的村民 |
| 附魔书竞争力 | 已采到交易数据的成年图书管理员 |

附魔书竞争力模式：

- **绿色**：该村民至少有一项附魔书交易，不存在更便宜的等价交易。
- **红色**：存在更便宜的等价交易。同价并列不互相淘汰。
- 比较范围是本会话的全部记录，不分维度。
- 打开已记录村民的交易界面时，会话内最低价的附魔书交易行会被绿框标出。
- 未采到交易数据的村民不标记（抓取失败时流程中已有警告）。
- 其他来源（例如 offers-hud）的交易数据会刷新标记，但不会新增记录、改变计数或写入报告。
- 描边通过客户端假队伍实现，**可能连带改变村民名牌颜色**（已知附带效果）。
- 若“记录全部交易”关闭且未选择图书管理员附魔书类别，开启时会警告一次（判定不可靠）。

### 确认界面

**重名冲突**：`/census start <名称>` 与已有文件重名时弹出确认，并预览最终报告文件名。

| 冲突情况 | 可选操作 |
|----------|----------|
| 存在同名半成品 | 继续半成品 / 重新开始 / 取消 |
| 仅存在同名已完成报告 | 沿用此名 / 改名新建 / 取消 |
| 半成品来自其他维度 | 强行继续 / 归档并开始 / 取消 |
| 半成品已损坏 | 归档并开始 / 取消 |

关闭 `startConflictPrompt` 后直接开始，仅在存在半成品时提示一次。

**续接半成品**：进入仍有半成品的世界约 1 秒后弹出，显示文件、世界、维度与 `已加载 / 已记录` 数量（每 10 gt 刷新）。

- 继续统计：载入并复核已加载村民。
- 保存半成品：留在磁盘，稍后可用 `/census resume` 续接。
- 放弃：归档为 `_orphan_<时间戳>`。
- 未加载的村民保留原有数据并标记“本次未复核”，不会被删除。

关闭 `resumePrompt` 后只发 action bar 提示，命令仍然可用。

### 输出文件

```
<minecraft>/villager_census/<world>/<name>_<dimension>_<yyyyMMdd_HHmmss>.census.txt
<minecraft>/villager_census/<world>/<name>_<dimension>_<yyyyMMdd_HHmmss>.census.json   (可选)
<minecraft>/villager_census/<world>/<name>_<dimension>.census.draft.json               (半成品)
```

- `/census stop` 总会写出 TXT 报告。
- JSON 仅在 `outputJson` 开启时写出（默认关闭）。
- `/census fork` 优先读取 JSON，不存在时解析 TXT。

### 配置

打开方式：MaLiLib 配置界面（快捷键默认无绑定），或 ModMenu 中的 `Villager Census`。

| 选项 | 默认 | 说明 |
|------|------|------|
| `triggerItem` | `minecraft:enchanted_book` | 右键村民所用的触发物品 |
| `triggerItems` | 空 | 额外触发物品，每行一个 |
| `triggerItemTag` | 空 | 额外触发物品标签，如 `#minecraft:books` |
| `recordAllTrades` | 开 | 记录全部交易；关闭时只记录选中的类别 |
| `selectedCategories` | 空 | 筛选用的类别键（`职业/id`），建议用配置界面的“选择交易类别…”编辑 |
| `glowingMarker` | 开 | 给已统计村民加发光描边 |
| `markWhenNotHolding` | 开 | 手持非触发物品时仍显示标记 |
| `passThrough` | 关 | 准星跳过本次已统计的村民，选中其后方实体 |
| `recordCoordinates` | 开 | 记录村民自身坐标（不推断工作方块） |
| `outputJson` | 关 | 结束时额外写出 JSON |
| `hudEnabled` | 开 | 显示 HUD |
| `hudPosition` | 左上 | HUD 贴靠位置 |
| `hudOffsetX` / `hudOffsetY` | 0 | HUD 像素偏移 |
| `offersTimeoutTicks` | 10 | 等待交易包的超时，超时后回退客户端数据 |
| `autoSaveDraft` | 开 | 退出世界时保存未完成会话 |
| `autoSaveIntervalSeconds` | 0 | 每 N 秒自动保存半成品；0 = 仅在退出时保存 |
| `offersHudCompat` | 开 | 与 offers-hud 共存，保证只发一次关窗包 |
| `resumePrompt` | 开 | 进世界时弹出续接界面 |
| `startConflictPrompt` | 开 | 重名时先确认 |
| `debugLog` | 关 | 输出调试日志 |

快捷键（默认均无绑定）：`openConfigGui` 打开配置、`toggleStats` 暂停/恢复、`undoLast` 撤销、`toggleBookMarker` 切换附魔书标记。

### HUD

普查进行中显示：

- 会话名与维度
- 已统计数量、职业数、幼年数
- 当前标记模式
- 待确认更新
- 抓取中的 `等待交易回包 X/Y tick`
- 续普查时的 `复核 X/N` 进度
- 最近一只村民的职业、坐标、血量与备注

HUD 无背景色块，位置与偏移可配置。没有会话但存在半成品时，显示半成品名、`已加载 X/N` 与命令提示。

### 交易类别

`villagercensus/trade_categories.json` 覆盖全部 13 个职业、共 **156** 个类别。

- 同义变体已合并：彩色物品、价格顺序互换、普通/探险地图、各类船等。
- 合并掉的旧类别 id 保留为别名，旧选择继续生效。
- 同一物品的“有附魔 / 无附魔”交易分开成两个类别，可分别选择。
- 未命中目录的交易回退为物品三元组标签。

### 兼容性

- **offers-hud**：可共存。本 Mod 从不取消 offers 包，预览不受影响；双方都发关窗包时由 `offersHudCompat` 保证只发一次。
- **itemscroller-sakura**：可共存，本 Mod 不取消或抢占原版交互。
- **服务器**：拦截交易界面并非原版交互模式，多人服务器上请自行评估风险。

### 说明与限制

- 只记录村民自身坐标，不查询或推断工作方块。
- 有职业的成年村民若在 `offersTimeoutTicks` 内没收到交易包，会标记“未取得交易数据”并警告。
- 退出世界会结束内存中的会话（未完成会话已存为半成品）；重进世界不会自动继续，需确认或 `/census resume`。
- 归档过的半成品（文件名含 `_orphan_`）不会再作为续接候选、fork 来源或重名冲突。
- `/census fork` 按会话名称匹配；同维度优先于跨维度，同维度内半成品 > JSON > TXT。
- TXT 报告不含 UUID 与幼年明细；fork 后对已加载且坐标、职业吻合的村民回填 UUID。
- 目前仅支持 Fabric，无 NeoForge 版本。

### 构建

```bash
./gradlew build              # 编译并打包全部已注册版本
./gradlew test               # 纯逻辑单元测试
./gradlew stonecutterSwitch  # 切换活动版本
```

产物位于 `versions/<mc>/build/libs/`。活动版本在 `stonecutter.gradle.kts` 中配置。

---

## English

### What it is

- **Client-only**: install it on the client, nothing is needed on the server.
- Right-click villagers while holding the trigger item; the merchant screen is intercepted and their offers are recorded automatically.
- Finish with a TXT report (optionally JSON) to compare enchantments, equipment and prices.
- Uses only client-side data. It does not modify the world and sends no extra packets to the server.

### Supported versions

| Minecraft | Java | MaLiLib |
|-----------|------|---------|
| 1.21.11   | 21   | 0.27.20 |
| 26.3      | 25   | 0.30.0  |

### Dependencies

- [Fabric Loader](https://fabricmc.net/) 0.19+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [MaLiLib (sakura-ryoko fork)](https://masa.dy.fi/maven/sakura-ryoko/) — config GUI, hotkeys and i18n
- Optional: [ModMenu](https://modrinth.com/mod/modmenu) — open the config from the mod list

### Install

1. Install Fabric Loader for your Minecraft version.
2. Put `fabric-api` and the matching `malilib` into `mods/`.
3. Put `villagercensus-<version>+<mc>.jar` into `mods/`.
4. Launch the game. The config file is `config/villagercensus.json`.

### Quick start

1. Hold the trigger item (default: enchanted book).
2. Run `/census start HallA`.
3. Right-click villagers one by one. The merchant screen is suppressed and each villager is recorded.
4. Run `/census stop` to write the report.

### Commands

| Command | Purpose |
|---------|---------|
| `/census start <name>` | Start a census in the current dimension |
| `/census fork <name>` | Fork a draft or report by session name and keep counting |
| `/census stop` | Finish and write the report |
| `/census abort` | Discard the session without writing a report |
| `/census undo` | Undo the last entry |
| `/census remark <text>` | Add a remark to the last villager |
| `/census status` | Show the session status |
| `/census resume` | Continue the found draft |
| `/census discard` | Archive the found draft |
| `/census pause` | Pause or resume counting |
| `/census reverse` | Toggle whether counted or uncounted villagers glow |
| `/census books` | Toggle librarian book-competitiveness markers |
| `/census help` | Show help |

### Marker modes

Three marker modes exist while a census runs; the HUD always shows the active one. The book mode stashes the normal/reversed state and restores it when turned off.

| Mode | Glowing villagers |
|------|-------------------|
| Normal | Counted villagers |
| Reversed | Uncounted villagers |
| Book competitiveness | Adult librarians with captured trade data |

Book competitiveness mode:

- **Green**: at least one enchanted-book trade has no cheaper equal offer in the session.
- **Red**: a cheaper equal offer exists. Equal prices do not eliminate each other.
- The comparison covers every record in the session, regardless of dimension.
- Opening the merchant screen of a recorded villager frames every lowest-price enchanted-book row in green.
- Librarians whose trade data was never captured are not marked; the capture flow already warns about them.
- Trade data from other sources (for example offers-hud) refreshes the markers but never adds a record, changes a count or reaches a report.
- The outline uses client-only fake teams and **may also change the villagers' name-tag colour** (known side effect).
- With `recordAllTrades` off and the librarian enchanted-book category unselected, the mode warns once because the verdict would be unreliable.

### Confirmation screens

**Name conflict**: `/census start <name>` shows a confirmation when the name collides with an existing file, including a preview of the final report file name.

| Situation | Options |
|-----------|---------|
| Draft with the same name | Continue draft / Restart / Cancel |
| Only finished reports with the same name | Keep this name / Rename / Cancel |
| Draft from another dimension | Force continue / Archive and start / Cancel |
| Corrupt draft | Archive and start / Cancel |

With `startConflictPrompt` off, the census starts immediately and only warns about a draft.

**Resume draft**: entering a world that still holds a draft shows a screen after about one second, with the file, world, dimension and `loaded / recorded` counts (refreshed every 10 gt).

- Continue: load the draft and verify loaded villagers.
- Keep draft: leave it on disk and resume later with `/census resume`.
- Discard: archive it as `_orphan_<timestamp>`.
- Unloaded villagers keep their data and are marked "not verified this time"; they are never deleted.

With `resumePrompt` off you only get an action-bar hint; the commands keep working.

### Output files

```
<minecraft>/villager_census/<world>/<name>_<dimension>_<yyyyMMdd_HHmmss>.census.txt
<minecraft>/villager_census/<world>/<name>_<dimension>_<yyyyMMdd_HHmmss>.census.json   (optional)
<minecraft>/villager_census/<world>/<name>_<dimension>.census.draft.json               (draft)
```

- `/census stop` always writes the TXT report.
- The JSON sidecar is written only when `outputJson` is enabled (default off).
- `/census fork` reads JSON when present and falls back to TXT otherwise.

### Configuration

Open it from the MaLiLib config screen (hotkey default: unbound) or from ModMenu (`Villager Census`).

| Option | Default | Description |
|--------|---------|-------------|
| `triggerItem` | `minecraft:enchanted_book` | Held item used to right-click villagers |
| `triggerItems` | empty | Extra trigger items, one per line |
| `triggerItemTag` | empty | Extra trigger item tag, e.g. `#minecraft:books` |
| `recordAllTrades` | on | Record every trade; off records only selected categories |
| `selectedCategories` | empty | Category keys (`profession/id`), best edited with the "Select Trade Categories..." picker |
| `glowingMarker` | on | Mark recorded villagers with Glowing |
| `markWhenNotHolding` | on | Keep markers visible when not holding the trigger item |
| `passThrough` | off | Crosshair skips counted villagers to target the entity behind them |
| `recordCoordinates` | on | Record the villager's own position (workstations are never inferred) |
| `outputJson` | off | Also write a JSON report on finish |
| `hudEnabled` | on | Show the HUD |
| `hudPosition` | top-left | HUD anchor position |
| `hudOffsetX` / `hudOffsetY` | 0 | HUD pixel offset |
| `offersTimeoutTicks` | 10 | Offers packet timeout before falling back to client data |
| `autoSaveDraft` | on | Save an unfinished session on world exit |
| `autoSaveIntervalSeconds` | 0 | Auto-save the draft every N seconds; 0 = only on exit |
| `offersHudCompat` | on | Coexist with offers-hud and send exactly one close packet |
| `resumePrompt` | on | Show the resume screen on world join |
| `startConflictPrompt` | on | Ask for confirmation on a name conflict |
| `debugLog` | off | Extra logging |

Hotkeys (all unbound by default): `openConfigGui`, `toggleStats`, `undoLast`, `toggleBookMarker`.

### HUD

While a census runs the HUD shows:

- session name and dimension
- villager, profession and baby counts
- the current marker mode
- a pending update
- a `waiting for offers X/Y ticks` line while capturing
- `verify X/N` progress after resuming
- the last villager's profession, position, health and remark

The HUD has no background box; its anchor and offset are configurable. With no session but a draft present, it shows the draft name, `loaded X/N` and the commands.

### Trade categories

`villagercensus/trade_categories.json` covers all 13 professions with **156** categories.

- Synonymous variants are merged: colour variants, cost-order swaps, plain vs. explorer maps, boat wood types, and so on.
- Replaced ids are kept as aliases, so older selections keep working.
- Enchanted and unenchanted variants of the same item stay separate and can be selected independently.
- Trades matching no entry fall back to an item-triple label.

### Compatibility

- **offers-hud**: coexists. This mod never cancels the offers packet, so previews keep working; when both would close the container, `offersHudCompat` ensures exactly one close packet.
- **itemscroller-sakura**: coexists; this mod never cancels or competes for the vanilla interaction.
- **Servers**: suppressing the merchant GUI is not a vanilla interaction pattern. Use your own judgement on multiplayer servers.

### Notes and limitations

- Only the villager's own position is recorded; workstations are never queried or inferred.
- A professed adult villager whose offers do not arrive within `offersTimeoutTicks` is marked "no trade data" and warns.
- Leaving a world ends the in-memory session (an unfinished session is saved as a draft); re-entering never continues silently, so confirm the prompt or run `/census resume`.
- Archived drafts (`_orphan_` in the file name) are never offered again as a resume candidate, fork source or name conflict.
- `/census fork` matches by session name; same-dimension sources beat cross-dimension ones, and within a dimension draft > JSON > TXT.
- TXT reports contain no UUIDs or baby detail lines; fork recovers UUIDs for loaded villagers matching position and profession.
- Fabric only for now (no NeoForge target).

### Build

```bash
./gradlew build              # compile and package every registered version
./gradlew test               # pure-logic unit tests
./gradlew stonecutterSwitch  # change the active version
```

Artifacts land in `versions/<mc>/build/libs/`. The active version is set in `stonecutter.gradle.kts`.

---

## Acknowledgements / 致谢

Many patterns and much of the version handling were adapted from these projects:

本项目的许多模式与版本处理方式改编自以下项目：

- **offers-hud** (naari3) — mixin approach for interactions and offers, and the "read offers without cancelling" rule.
- **itemscroller-sakura** (masa, sakura-ryoko) — MaLiLib config/hotkey/world-load integration and villager data semantics.
- **malilib-sakura** (masa, sakura-ryoko) — config GUI, hotkeys, i18n and file utilities.
- **fabric-api** — client command registration and mapping references.
- **fabric-mod-template** — project structure reference.
- **guardian** (TISUnion) — decompiled Minecraft source history used to build the trade category catalog.

## License / 许可

Released under the [Unlicense](LICENSE) (public domain). 本项目以 [Unlicense](LICENSE) 发布（公有领域）。
