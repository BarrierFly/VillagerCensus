# Villager Census / 村民普查

A **client-only** Fabric mod that helps you census villagers in large or hand-built
trading halls: batch right-click villagers with a trigger item, then export a readable
trade report so you can pick the best enchantments, equipment and prices.

一个**纯客户端**的 Fabric Mod，用于在大型村庄或手工刷村民场景中批量统计村民的职业与
交易信息，并导出可读报告，方便挑选优质交易（附魔书、装备、低价交易等）。

---

## Supported versions / 支持版本

| Minecraft | Java | malilib | Status |
|-----------|------|---------|--------|
| 1.21.11   | 21   | 0.27.20 | built / 已构建 |
| 26.3      | 25   | 0.30.0  | built / 已构建 |

Built with **Stonecutter** (single branch, multi-version preprocessing).
使用 **Stonecutter** 单分支多版本预处理。

## Dependencies / 依赖

- [Fabric Loader](https://fabricmc.net/) + [Fabric API](https://modrinth.com/mod/fabric-api)
- [MaLiLib (sakura-ryoko fork)](https://masa.dy.fi/maven/sakura-ryoko/) — config GUI, hotkeys,
  i18n and file/data utilities / 配置界面、快捷键、i18n 与文件/数据工具

## Install / 安装

1. Install Fabric Loader for the target Minecraft version.
2. Put `fabric-api` and the matching `malilib` jar in `mods/`.
3. Put the matching `villagercensus-*.jar` in `mods/`.

---

## Usage / 用法

Start a census in the current dimension, then right-click villagers while holding the
trigger item (default: enchanted book). The merchant screen is suppressed; the villager's
offers are captured and counted.

先在当前维度开始一次普查，然后手持触发物品（默认附魔书）右键村民。交易界面会被拦截，
交易数据被记录并计数。

```
/census start <name>    start a census / 开始普查
/census stop            finish and write the report / 结束并输出报告
/census abort           discard the session / 放弃会话
/census undo            undo the last entry / 撤销上一次
/census remark <text>   set a remark on the last villager / 给上一只村民加备注
/census status          show session status / 查看会话状态
/census resume          continue the found draft / 续接找到的半成品
/census discard         archive the found draft / 归档半成品
/census pause           pause/resume statistics / 暂停或恢复统计
/census help            show help / 显示帮助
```

Output files / 输出文件:

```
<minecraft>/villager_census/<world>/<name>_<dimension>_<yyyyMMdd_HHmmss>.census.txt
<minecraft>/villager_census/<world>/<name>_<dimension>_<yyyyMMdd_HHmmss>.census.json   (optional / 可选)
<minecraft>/villager_census/<world>/<name>_<dimension>.census.draft.json               (draft / 半成品)
```

## Configuration / 配置

Open the malilib config screen (hotkey default: none) or use the ModMenu entry
`Villager Census` → config.

- `triggerItem` — held item used to right-click (registry id, default `minecraft:enchanted_book`)
- `recordAllTrades` — record every trade; when off only selected categories are recorded
- `selectedCategories` — category ids used when the above is off
- `glowingMarker` — mark recorded villagers with Glowing
- `recordCoordinates` — record the villager's own position only
- `outputJson` — also write a JSON report
- `hudEnabled` — show the census HUD
- `offersTimeoutTicks` — packet wait timeout before falling back to client data
- `autoSaveDraft` — save an unfinished session on world exit
- `debugLog` — extra logging

Hotkeys (all default `none`): `openConfigGui`, `toggleStats`, `undoLast`.

## How it works / 工作原理

- `MultiPlayerGameMode.interact` (HEAD) registers the right-clicked villager as the
  pending target; the vanilla interaction still sends its packet.
- `ClientPacketListener.handleMerchantOffers` (HEAD, **never cancelled**) reads
  `offers` / `villagerLevel` / `villagerXp` and associates them with the pending target
  using `containerId` (from `handleOpenScreen`) plus level. If no `containerId` is
  available it degrades to a **weak association**.
- `ClientPacketListener.handleOpenScreen` (HEAD, cancellable) records the container id and
  suppresses the merchant screen, then sends one `ServerboundContainerClosePacket`.

## Known limitations & risks / 已知限制与风险

- **Server rules / anti-cheat.** Suppressing the merchant GUI and closing the container is
  not a vanilla interaction pattern. Use at your own risk on multiplayer servers.
  / 拦截交易界面并非原版交互模式，在多人服务器上使用风险自负。
- **offers-hud coexistence.** Both mods hook the same methods. This mod never cancels the
  offers packet, so offers-hud previews keep working. If offers-hud is also installed it may
  send the close packet as well; close-packet duplication is harmless.
  / 本 Mod 从不取消 offers 包，不影响 offers-hud 预览；两者可能各发一次关窗包，无害。
- Enchantment max level is resolved reflectively and may be `-1` (not shown) on some setups.
- The draft continuation flow is command based (`/census resume` / `discard`) instead of a
  custom confirmation screen. Loaded villagers are re-checked once on resume.
- Workstation coordinates are intentionally never recorded or inferred (only the villager's
  own position), per the design.

## Deviations from the original plan / 与原规划的差异

- **Fabric only** for now (no NeoForge target). 首版只做 Fabric。
- The trade-category catalog (`villagercensus/trade_categories.json`, **303 categories across
  all 13 professions**) is generated from the decompiled vanilla trades in the `guardian`
  repository (`mojmap/vineflower`): **1.21.11** commit `192e7132b4` (explicit `ItemListing`
  constructors) and **26.3** commit `d5822ca2a1` (data-driven `VillagerTrade.builder` plus
  `villager_trade` tags). Both sets are merged. Trades that still match no entry fall back to
  an item-triple label.
  交易类别目录由 guardian 反编译源码生成，并合并 1.21.11 与 26.3（共 303 项，覆盖全部职业）；
  未命中的交易回退为物品三元组。
- Continuation uses commands rather than a custom screen (see above).
- The initial version targets the plan's milestones roughly through **M5/M6** for the two
  first versions (1.21.11, 26.3).

## Build / 构建

```bash
./gradlew build
```

Stonecutter's active version is configured in `stonecutter.gradle.kts`. The build compiles
and packages **all** registered versions; per-version jars land in
`versions/<mc>/build/libs/`.

```bash
./gradlew stonecutterSwitch   # change the active version / 切换活动版本
```

## Acknowledgements / 致谢

This project was implemented by studying the following projects that shipped in the same
workspace. Many patterns (Stonecutter setup, mixin/version preprocessing, malilib
integration, trade data handling) are adapted from them:

本项目的实现参考了同一工作区中的以下项目，许多模式（Stonecutter 配置、Mixin 与版本预处理、
malilib 接入、交易数据处理）改编自它们：

- **offers-hud** (naari3) — `MultiPlayerGameMode.interact` / `handleMerchantOffers` /
  `handleOpenScreen` mixin approach, the "read offers without cancelling" rule, and the
  Stonecutter + Architectury Loom version-preprocessing setup, including the 26.1+
  `loom-no-remap` handling.
- **itemscroller-sakura** (masa, sakura-ryoko) — malilib config/hotkey/world-load wiring,
  `TradeType` triple concept, and villager data storage semantics.
- **malilib-sakura** (masa, sakura-ryoko) — config GUI, hotkeys, i18n, `StringUtils`,
  `FileUtils`, and tag data utilities used throughout.
- **fabric-api** — client command registration API and reference for mapping differences.
- **fabric-mod-template** — project layout reference.
- **guardian** (TISUnion) — decompiled Minecraft source history used to generate the trade
  category catalog from the exact `1.21.11` (`mojmap/vineflower`) vanilla trade tables.
- The original design document provided by the user (村民普查 Mod 规划).

## License / 许可

No formal license. Do whatever you want with it.
不提供正式许可证，我不管，您看着办。
