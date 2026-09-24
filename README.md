# Villager Census / 村民普查

A **client-only** Fabric mod for censusing villagers in large or hand-built trading halls:
batch right-click villagers with a trigger item, then export a readable report so you can
pick the best enchantments, equipment and prices.

一个**纯客户端**的 Fabric Mod，用于在大型村庄或手工刷村民场景中批量统计村民的职业与
交易信息，并导出可读报告，方便挑选优质交易（附魔书、装备、低价交易等）。

---

## Supported versions / 支持版本

| Minecraft | Java | MaLiLib | Status / 状态 |
|-----------|------|---------|---------------|
| 1.21.11   | 21   | 0.27.20 | built / 已构建 |
| 26.3      | 25   | 0.30.0  | built / 已构建 |

Built with **Stonecutter** (single branch, multi-version preprocessing).
使用 **Stonecutter** 单分支多版本预处理。

## Dependencies / 依赖

- [Fabric Loader](https://fabricmc.net/) + [Fabric API](https://modrinth.com/mod/fabric-api)
- [MaLiLib (sakura-ryoko fork)](https://masa.dy.fi/maven/sakura-ryoko/) — 配置界面、快捷键、
  i18n 与文件/数据工具 / config GUI, hotkeys, i18n and file/data utilities

## Install / 安装

1. 为目标 Minecraft 版本安装 Fabric Loader。
2. 把 `fabric-api` 与对应版本的 `malilib` 放进 `mods/`。
3. 把对应版本的 `villagercensus-*.jar` 放进 `mods/`。

## Usage / 用法

Start a census in the current dimension, then right-click villagers while holding the
trigger item (default: enchanted book). The merchant screen is suppressed; the villager's
offers are captured and counted.

先在当前维度开始一次普查，然后手持触发物品（默认附魔书）右键村民。交易界面会被拦截，
交易数据被记录并计数。

```
/census start <name>    start a census / 开始普查
/census fork <name>     fork an existing draft/report and keep counting / fork 已有记录并继续统计
/census stop            finish and write the report / 结束并输出报告
/census abort           discard the session / 放弃会话
/census undo            undo the last entry / 撤销上一次
/census remark <text>   set a remark on the last villager / 给上一只村民加备注
/census status          show session status / 查看会话状态
/census resume          continue the found draft / 续接找到的半成品
/census discard         archive the found draft / 归档半成品
/census pause           pause/resume statistics / 暂停或恢复统计
/census reverse         toggle whether counted or uncounted villagers glow / 切换已统计/未统计发光
/census help            show help / 显示帮助
```

## Output files / 输出文件

```
<minecraft>/villager_census/<world>/<name>_<dimension>_<yyyyMMdd_HHmmss>.census.txt
<minecraft>/villager_census/<world>/<name>_<dimension>_<yyyyMMdd_HHmmss>.census.json   (optional / 可选)
<minecraft>/villager_census/<world>/<name>_<dimension>.census.draft.json               (draft / 半成品)
```

## Configuration / 配置

Open the malilib config screen (hotkey default: none) or use the ModMenu entry
`Villager Census`.
打开 malilib 配置界面（快捷键默认无绑定），或从 ModMenu 的 `Villager Census` 进入。

- `triggerItem` — 右键村民所用的手持物品（注册名，默认 `minecraft:enchanted_book`）
  / held item used to right-click (registry id).
- `recordAllTrades` — 记录全部交易；关闭时只记录被选中的类别
  / record every trade; when off only selected categories are recorded.
- `selectedCategories` — 上述关闭时用于筛选的类别键（`职业/id`）；建议用配置界面的
  “选择交易类别…”按钮编辑，界面已把同义变体（颜色、船、探险地图、价格顺序等）合并为一项
  / category keys (`profession/id`) used when the above is off; edit them with the config
  screen's "Select Trade Categories..." picker, which collapses variant trades into one entry.
- `glowingMarker` — 给已统计村民加发光描边；`/census reverse` 可反转为“未统计发光”
  / mark recorded villagers with Glowing; `/census reverse` flips it to mark uncounted ones.
- `recordCoordinates` — 只记录村民自身坐标 / record the villager's own position only.
- `outputJson` — 额外输出 JSON 报告 / also write a JSON report.
- `hudEnabled` — 显示普查 HUD / show the census HUD.
- `offersTimeoutTicks` — 等待交易包的超时，超时后回退客户端数据
  / packet wait timeout before falling back to client data.
- `autoSaveDraft` — 退出世界时保存未完成会话 / save an unfinished session on world exit.
- `debugLog` — 输出调试日志 / extra logging.

Hotkeys (all default unbound): `openConfigGui`, `toggleStats`, `undoLast`.
快捷键（默认均无绑定）：打开配置界面、暂停/恢复统计、撤销上一次。

## HUD / 界面显示

While a session is running the HUD shows the session name and dimension, the villager count,
profession count, baby count, the current marker mode (`/census reverse`), any pending update,
the `verify X/N` progress after resuming, and the last recorded villager (profession, position,
health, remark). It is drawn through the Fabric HUD API and stays visible until you stop or
abort; there is no background box.

普查进行中 HUD 会显示：会话名与维度、已统计数量、职业数、幼年数、当前标记模式
（`/census reverse`）、待确认更新、续普查时的 `verify X/N` 复核进度，以及最近一只村民的
职业、坐标、血量与备注。HUD 通过 Fabric HUD API 绘制，会一直显示到 stop/abort，且没有背景色块。

## How it works / 工作原理

- `MultiPlayerGameMode.interact` (HEAD) 只把被右键的村民登记为待关联目标，不取消原版交互，
  由原版正常发包。
  / only registers the pending target; the vanilla interaction still sends its packet.
- `ClientPacketListener.handleMerchantOffers` (HEAD, **never cancelled**) 读取
  `offers` / `villagerLevel` / `villagerXp`，用 `containerId`（来自 `handleOpenScreen`）加等级
  与待关联目标匹配，拿不到 `containerId` 时降级为**弱关联**。该回调运行在网络线程，只入队，
  真正的记录/提示在客户端 tick 处理。
  / reads the offers packet and associates it; runs on the network thread and only enqueues
  the packet. Record building and messaging happen on the client thread.
- `ClientPacketListener.handleOpenScreen` (HEAD, cancellable) 记录 container id、拦截交易界面，
  并回发一个 `ServerboundContainerClosePacket`。
  / records the container id, suppresses the merchant screen and sends one close packet.

## Notes & limitations / 说明与限制

- **服务器规则 / 反作弊。** 拦截交易界面并关闭容器并非原版交互模式，在多人服务器上使用
  风险自负。/ Suppressing the merchant GUI is not a vanilla interaction pattern; use at
  your own risk on multiplayer servers.
- **与 offers-hud 共存。** 两者注入同样的方法。本 Mod 从不取消 offers 包，因此 offers-hud
  的预览不受影响；两者可能各发一次关窗包，无害。
  / This mod never cancels the offers packet, so offers-hud previews keep working.
- 附魔最高等级通过反射获取，个别情况下可能取不到（不显示 `等级/最高`）。
  / Enchantment max level is resolved reflectively and may be unavailable (`-1`).
- 续普查为命令式：退出世界会结束内存中的会话（未完成会话已存为半成品），重进世界**不会**
  自动继续；用 `/census resume` 重新载入，之后会对已加载村民复核（HUD 显示 `verify X/N`）
  并重新施加发光标记。
  / Leaving a world ends the in-memory session (an unfinished session is saved as a draft);
  re-entering does not auto-continue. Use `/census resume` to reload, re-check and re-mark.
- 只记录村民自身坐标，不查询、不推断工作方块坐标。
  / Only the villager's own position is recorded; workstations are never inferred.
- 首版仅支持 Fabric（无 NeoForge）。
  / Fabric only for now (no NeoForge target).

## Trade category catalog / 交易类别目录

`villagercensus/trade_categories.json` 覆盖全部 13 个职业、共 **156** 个类别，由反编译的
原版交易表生成并合并 1.21.11 与 26.3，再经 `tools/catalog/normalize_catalog.py` 归一化
（未命中的交易回退为物品三元组标签）。归一化会合并同义变体：彩色物品（旗帜、羊毛、地毯、
床、染料、陶瓦、带釉陶瓦、蜡烛）、按价格顺序互换的同一交易（制箭师的箭/绿宝石）、以及普通
地图与各生态探险地图、各材质船等；被合并掉的旧类别 id 作为别名保留，旧选择继续生效。
同一物品的“有附魔/无附魔”两条交易**分开**成两个类别（如 2 级普通弓 `sell_bow` 与 4 级附魔弓
`sell_enchanted_bow`、3 级普通弩与 5 级附魔弩），可分别选择是否记录。

The catalog is generated from the decompiled vanilla villager trades, merged across 1.21.11
and 26.3, then normalized by `tools/catalog/normalize_catalog.py`; trades that match no entry
fall back to an item-triple label. Normalization collapses synonymous variants (colour variants
such as banners/wool/carpet/bed/dye/terracotta/glazed terracotta/candles, cost-order swaps such
as the fletcher's arrow/emerald, plain vs. explorer maps, and boat wood types). Replaced ids are
kept as aliases so older selections keep working. The enchanted and unenchanted variants of the
same item stay **separate** (e.g. the level-2 plain bow vs. the level-4 enchanted bow, and the
plain/ enchanted crossbow), so each can be recorded independently.

## Build / 构建

```bash
./gradlew build
```

Stonecutter 的活动版本在 `stonecutter.gradle.kts` 中配置；构建会编译并打包**全部**已注册
版本，产物位于 `versions/<mc>/build/libs/`。
The active Stonecutter version is set in `stonecutter.gradle.kts`; the build compiles and
packages **all** registered versions.

```bash
./gradlew stonecutterSwitch   # change the active version / 切换活动版本
```

## Acknowledgements / 致谢

Many patterns and much of the version handling were adapted from the following projects:

本项目的许多模式与版本处理方式改编自以下项目：

- **offers-hud** (naari3) — `MultiPlayerGameMode.interact` / `handleMerchantOffers` /
  `handleOpenScreen` mixin approach, the "read offers without cancelling" rule, and the
  Stonecutter + Architectury Loom version-preprocessing setup, including the 26.1+
  `loom-no-remap` handling.
- **itemscroller-sakura** (masa, sakura-ryoko) — malilib 配置/快捷键/世界加载的接入方式、
  交易三元组与村民数据存储语义。
- **malilib-sakura** (masa, sakura-ryoko) — 配置界面、快捷键、i18n、`StringUtils`、`FileUtils`
  及标签数据工具。
- **fabric-api** — 客户端命令注册 API，以及映射差异参考。
- **fabric-mod-template** — 工程结构参考。
- **guardian** (TISUnion) — 反编译的 Minecraft 源码历史库，用于生成交易类别目录。
