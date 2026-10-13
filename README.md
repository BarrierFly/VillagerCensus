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
/census fork <name>     fork a draft/report by session name and keep counting / 按会话名称 fork 已有记录并继续统计
/census stop            finish and write the report / 结束并输出报告
/census abort           discard the session / 放弃会话
/census undo            undo the last entry / 撤销上一次
/census remark <text>   set a remark on the last villager / 给上一只村民加备注
/census status          show session status / 查看会话状态
/census resume          continue the found draft / 续接找到的半成品
/census discard         archive the found draft / 归档半成品
/census pause           pause/resume statistics / 暂停或恢复统计
/census reverse         toggle whether counted or uncounted villagers glow / 切换已统计/未统计发光
/census books           toggle librarian book-competitiveness markers / 切换图书管理员附魔书竞争力标记
/census help            show help / 显示帮助
```

### Marker modes / 标记模式

While a census runs, glowing markers have three modes; `/census reverse` and `/census books`
switch between them and the HUD always shows the active one. The book mode stashes the
normal/reversed state and restores it when turned off.

普查进行中发光标记有三种模式；`/census reverse` 与 `/census books` 负责切换，HUD 始终显示
当前模式。附魔书竞争力模式开启期间会暂存常规/反转状态，关闭后恢复原状态。

- **Normal（常规）** — counted villagers glow. / 已统计的村民发光。
- **Reversed（反转）** — uncounted villagers glow. / 未统计的村民发光。
- **Book competitiveness（附魔书竞争力）** — only adult librarians whose offers were captured glow:
  green when at least one of their enchanted-book trades has no cheaper equal offer anywhere in the
  session, red otherwise (equal prices do not eliminate each other; librarians whose trade data was
  never captured are not marked at all — the capture flow already warns about them). The comparison
  covers every record in the session regardless of dimension and never touches reports or drafts.
  The glow outline is tinted via client-only fake teams (`vc_book_good` / `vc_book_bad`), which can
  also change the villagers' name-tag color as a known side effect; with `recordAllTrades` off and
  the librarian enchanted-book category unselected the mode warns once because the verdict would be
  unreliable.
  Trade data that arrives from any source (a vanilla trade screen, an offers-hud crosshair probe,
  …) refreshes the marker of the villagers this session already recorded, so the verdict follows
  the latest offers. Observed trades only feed the markers: they never add a record, never change a
  count and never reach a report or a draft, and they are not filtered by `recordAllTrades` /
  `selectedCategories` (that filtering serves the report, and applying it here would mark every
  librarian red). A freshly recorded villager wins over an older observation.
  任何来源（自己打开交易界面、offers-hud 准星探针等）收到的交易数据都会刷新**会话中已有记录**的
  村民标记，使判定跟上最新报价。观察到的交易只影响标记：不会新增记录、不改变计数、不进入报告与
  半成品，也不受 `recordAllTrades` / `selectedCategories` 筛选影响（筛选是为报告服务的，用在这里
  会把所有图书管理员判成红色）；刚统计进来的记录优先于更早的观察数据。
  仅**已采到交易数据**的成年图书管理员发光：会话内不存在更低价的同附魔组合、同币种交易即为
  “有竞争力”（绿），否则“无竞争力”（红）；同价并列不互相淘汰；未采到交易数据的图书管理员
  完全不标记（抓取失败时普查流程已有警告提示重试）。比较范围为会话内全部记录（不分维度），
  不影响报告与半成品。描边颜色通过纯客户端假队伍（`vc_book_good` / `vc_book_bad`）实现，可能
  连带改变村民名牌颜色（已知附带效果）；若 `recordAllTrades` 关闭且未选择图书管理员附魔书类别，
  开启时会警告一次（此时判定不可靠）。

### Confirmation screens / 确认界面

- **重名冲突。** `/census start <名称>` 与当前世界中已有的半成品或报告重名时会弹出确认界面，
  按冲突类型给出选项，并预览最终落盘的报告文件名：
  - 存在同名半成品：**继续半成品** / **重新开始**（删除旧半成品）/ 取消；
  - 仅有同名已完成报告：**沿用此名**（新时间戳另存）/ **改名新建** / 取消；
  - 半成品来自其他维度：**强行继续** / **归档并开始** / 取消；
  - 半成品损坏：**归档并开始** / 取消。
  关闭 `startConflictPrompt` 后恢复旧行为：直接开始，仅在存在半成品时提示一次。
  A name that collides with a draft or report in the current world opens a confirmation screen with
  options per collision type and a preview of the exact report file name. Turning
  `startConflictPrompt` off restores the previous behaviour (start immediately, warn only about a
  draft).
- **续接半成品。** 进入仍有半成品的世界时，约 1 秒后弹出续接界面，显示半成品文件、当前世界与
  维度、`已加载 / 已记录 X / Y`（每 10 gt 刷新）：**继续统计** / **保存半成品**（留在磁盘上稍后
  再续）/ **放弃**（归档为 `_orphan_<时间戳>`）。未加载的村民保留原有数据并标记“本次未复核”，
  不会被判失败或删除。命令 `/census resume`、`/census discard` 仍然可用，与界面共享同一状态机；
  关闭 `resumePrompt` 后只发 action bar 提示。
  Joining a world that still holds a draft opens a resume screen (after ~1 s, so it does not fight
  the loading screen) showing the file, the current world/dimension and `loaded / recorded X / Y`
  refreshed every 10 gt. Unloaded villagers keep their data and are only marked "not verified this
  time". The commands remain available and share the same state machine; turning `resumePrompt` off
  falls back to the action-bar hint.

## Output files / 输出文件

```
<minecraft>/villager_census/<world>/<name>_<dimension>_<yyyyMMdd_HHmmss>.census.txt
<minecraft>/villager_census/<world>/<name>_<dimension>_<yyyyMMdd_HHmmss>.census.json   (optional / 可选)
<minecraft>/villager_census/<world>/<name>_<dimension>.census.draft.json               (draft / 半成品)

Every `/census stop` writes the TXT report. The JSON sidecar is only written when
`outputJson` is enabled (default off); `/census fork` reads it when present and otherwise
falls back to the TXT report. /
每次 `/census stop` 都会写出 TXT 报告；JSON 仅当启用 `outputJson`（默认关闭）时写出。
`/census fork` 优先读 JSON，找不到时回退读取 TXT 报告。
```

## Configuration / 配置

Open the malilib config screen (hotkey default: none) or use the ModMenu entry
`Villager Census`.
打开 malilib 配置界面（快捷键默认无绑定），或从 ModMenu 的 `Villager Census` 进入。

- `triggerItem` — 右键村民所用的手持物品（注册名，默认 `minecraft:enchanted_book`）
  / held item used to right-click (registry id).
- `triggerItems` — 额外的触发物品注册名列表（每行一个）/ extra trigger item registry ids, one per line.
- `triggerItemTag` — 亦可触发统计的物品标签（如 `#minecraft:books`），留空不启用
  / item tag that also triggers (e.g. `#minecraft:books`); empty disables it.
- `recordAllTrades` — 记录全部交易；关闭时只记录被选中的类别
  / record every trade; when off only selected categories are recorded.
- `selectedCategories` — 上述关闭时用于筛选的类别键（`职业/id`）；建议用配置界面的
  “选择交易类别…”按钮编辑，界面已把同义变体（颜色、船、探险地图、价格顺序等）合并为一项
  / category keys (`profession/id`) used when the above is off; edit them with the config
  screen's "Select Trade Categories..." picker, which collapses variant trades into one entry.
- `glowingMarker` — 给已统计村民加发光描边；`/census reverse` 反转为“未统计发光”，
  `/census books` 切换为图书管理员附魔书竞争力双色标记（见“标记模式”）
  / mark recorded villagers with Glowing; `/census reverse` flips it to mark uncounted ones and
  `/census books` switches to the librarian book-competitiveness mode (see "Marker modes").
- `markWhenNotHolding` — 关闭后主手持的不是触发物品时不再显示发光标记
  / when off, glowing markers are hidden unless the trigger item is in the main hand.
- `passThrough` — 普查中手持触发物品时，准星跳过本次已统计的村民以选中其后方实体（默认关闭；
  其后没有实体时仍选中该村民）
  / while a census runs and the trigger item is held, the crosshair skips villagers this session
  already counted so the entity behind them can be targeted (default off; the counted villager stays
  selected when there is nothing behind it).
- `recordCoordinates` — 记录村民自身坐标；关闭时报告省略坐标、且不比较坐标差异
  / record the villager's own position; when off the report omits it and coord diffs are skipped.
- `outputJson` — 结束统计时额外写出 `.census.json`（默认关闭；关闭后 fork 读 TXT）
  / also write a `.census.json` sidecar on finish (default off; fork then reads the TXT).
- `hudEnabled` — 显示普查 HUD / show the census HUD.
- `hudPosition` / `hudOffsetX` / `hudOffsetY` — HUD 贴靠的边角（top/bottom × left/right、center）
  与像素偏移
  / the corner or center the HUD anchors to, and its pixel offset.
- `offersTimeoutTicks` — 等待交易包的超时，超时后回退客户端数据
  / packet wait timeout before falling back to client data.
- `autoSaveDraft` — 退出世界时保存未完成会话 / save an unfinished session on world exit.
- `autoSaveIntervalSeconds` — 每 N 秒定时保存进行中的半成品（0=仅退出时保存，暂停时不保存）
  / periodically save the running draft every N seconds (0 = only on exit; paused sessions are never saved).
- `offersHudCompat` — 装了 offers-hud 时由它关闭被拦截的交易界面，保证只发一次关窗包；关闭后
  始终由本 Mod 自己发送
  / with offers-hud installed, let it close the suppressed merchant container so exactly one close
  packet is sent; when off this mod always closes it itself.
- `resumePrompt` — 进世界时弹出续接确认界面
  / show the resume confirmation screen when joining a world that still holds a draft.
- `startConflictPrompt` — `/census start` 重名时弹出确认界面
  / ask for confirmation when `/census start` collides with an existing draft or report.
- `debugLog` — 输出调试日志 / extra logging.

Hotkeys (all default unbound): `openConfigGui`, `toggleStats`, `undoLast`, `toggleBookMarker`.
快捷键（默认均无绑定）：打开配置界面、暂停/恢复统计、撤销上一次、切换附魔书竞争力标记。

## HUD / 界面显示

While a session is running the HUD shows the session name and dimension, the villager count,
profession count, baby count, the current marker mode (normal / reversed / book competitiveness),
any pending update, a `waiting for offers X/Y ticks` line while a villager is being captured, the
`verify X/N` progress after resuming, and the last recorded villager (profession, position, health,
remark). It is drawn through the Fabric HUD API, stays visible until you stop or abort, and has no
background box. The anchor corner and pixel offset are configurable (`hudPosition`, `hudOffsetX`,
`hudOffsetY`). With no active session but a draft present it shows the draft name and `loaded X/N`
plus the commands.

普查进行中 HUD 会显示：会话名与维度、已统计数量、职业数、幼年数、当前标记模式
（常规 / 反转 / 附魔书竞争力）、待确认更新、抓取中的 `等待交易回包 X/Y tick`、续普查时的
`verify X/N` 复核进度，以及最近一只村民的职业、坐标、血量与备注。HUD 通过 Fabric HUD API 绘制，
会一直显示到 stop/abort，没有背景色块，贴靠的边角与偏移可通过 `hudPosition`、`hudOffsetX`、
`hudOffsetY` 调整。没有会话但存在半成品时显示半成品名与 `loaded X/N` 及命令提示。

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
- `ProjectileUtil.getEntityHitResult` (RETURN) 只在开启 `passThrough`、普查进行中且主手持触发物品时
  生效：若命中的是本次已统计的村民，就排除它重跑一次射线，改为返回其后方实体；后方没有实体时保留
  原命中。
  / only active with `passThrough` on, a running session and the trigger item in hand: if the hit is
  a villager this session already counted, the ray is repeated without it so the entity behind wins;
  the original hit is kept when there is nothing behind.
- 附魔书竞争力模式的绿/红描边来自纯客户端假队伍：发光描边颜色取 `Entity#getTeamColor`，本 Mod 在
  客户端记分板上建 `vc_book_good`/`vc_book_bad` 两个队伍并写入成员，不向服务端发包；队伍对象随世界
  销毁，村民被释放或退出模式时移出队伍。
  / The green/red outlines of the book-competitiveness mode come from client-only fake teams: the
  glow outline color is `Entity#getTeamColor`, so the mod joins librarians into `vc_book_good` /
  `vc_book_bad` on the client scoreboard. Nothing is sent to the server; teams die with the world and
  members are released when their marker is.

## Compatibility / 兼容性

- **offers-hud。** 本 Mod 注入的 `handleOpenScreen` 优先级为 900（早于 offers-hud 的默认 1000），
  因此 container id 一定会在任何人取消包之前被记录；本 Mod **从不取消** offers 包，offers-hud 的
  预览不受影响。两者都会在拦截后发一次关窗包，因此 `compat/offershud/OffersHudCompat` 会只读探测
  offers-hud 的 `openWindow` 标志：当它会自己发关窗包时本 Mod 不再发第二次，保证**只发一次**；反射
  探测失败时回退为自关闭，绝不会把容器留在打开状态。用 `offersHudCompat` 关闭该兼容可强制始终由
  本 Mod 发送。
  / Our `handleOpenScreen` hook runs at priority 900 so the container id is always recorded, and the
  offers packet is never cancelled. Both mods would otherwise send a container close, so
  `compat/offershud/OffersHudCompat` read-probes offers-hud's `openWindow` flag and skips the second
  close packet; if the reflective probe fails it falls back to closing the container itself.
  `offersHudCompat` forces the self-contained path.
- **itemscroller-sakura。** `MultiPlayerGameMode.interact` 只在 HEAD 登记待关联目标，不取消交互，
  不抢交互包。
- **未纳入本版本的规划项。** offers-hud 的“暂停其准星拉包”开关没有实现：要阻止它发
  `ServerboundInteractPacket` 就得改写另一个 Mod 的客户端 tick 处理，风险与维护成本都不划算；
  需要时用 `passThrough` 或直接关掉 offers-hud 的射线功能即可。真正的半透明渲染（规划 M8 /
  9.4）与 1.21.1 / 1.21.10 / 1.19.4 三个额外版本同样暂缓，理由见 `AGENTS.md` 与
  `HANDOVER.md`。

## Notes & limitations / 说明与限制

- **服务器规则 / 反作弊。** 拦截交易界面并关闭容器并非原版交互模式，在多人服务器上使用
  风险自负。/ Suppressing the merchant GUI is not a vanilla interaction pattern; use at
  your own risk on multiplayer servers.
- **与 offers-hud 共存。** 两者注入同样的方法。本 Mod 从不取消 offers 包，也不取消其他 Mod 的
  处理结果；关窗包只由一方发出（见“兼容性”）。
  / This mod never cancels the offers packet, so offers-hud previews keep working, and only one of
  the two mods sends the container close.
- `/census fork <name>` 按记录里的会话名称匹配半成品或已完成报告（不再要求当前维度一致）。
  来源优先级：同维度统一优先于跨维度（因此同维度的 TXT 优先于跨维度的 JSON）；同维度内
  半成品 > JSON 报告 > TXT 报告，再按文件名最新优先。找不到 JSON 时直接解析 TXT 报告，
  TXT 已内嵌物品/职业/附魔注册名，但**不含 UUID 与幼年明细**，fork 后会对当前已加载、
  坐标与职业吻合的村民回填 UUID。只匹配到跨维度来源时照常开始并在聊天栏警告，源文件不改动。
  / `/census fork <name>` matches a draft or completed report by its recorded session name
  (the current dimension is no longer required). Same-dimension sources always win over
  cross-dimension ones (so a same-dimension TXT beats a cross-dimension JSON); within the same
  dimension: draft > JSON report > TXT report, newest first. When no JSON exists the TXT report
  is parsed directly; it embeds item/profession/enchantment registry ids but has **no UUIDs or
  baby details**, so loaded villagers matching by position and profession recover their UUID.
  A cross-dimension source still starts the fork with an action-bar warning, and the source is
  never modified.
- 有职业的成年村民若在 `offersTimeoutTicks` 内没有收到交易包，会在记录时发出警告并标记
  “未取得交易数据”，其余客户端字段照常记录。
  / If a professed adult villager's offers do not arrive within `offersTimeoutTicks`, a warning
  is shown and the record is marked "no trade data"; the other client-side fields are still
  recorded.
- 续普查为命令式：退出世界会结束内存中的会话（未完成会话已存为半成品），重进世界**不会**自动继续；
  默认会弹出续接确认界面（见“确认界面”），也可以随时用 `/census resume` 重新载入，之后会对已加载
  村民复核（HUD 显示 `verify X/N`）并重新施加发光标记。
  / Leaving a world ends the in-memory session (an unfinished session is saved as a draft); re-entering
  never continues silently. A resume screen is shown by default (see "Confirmation screens"), and
  `/census resume` still reloads, re-checks and re-marks on demand.
- 归档过的半成品（文件名含 `_orphan_`）不会再被当作续接候选、fork 来源或重名冲突。
  / Archived drafts (`_orphan_` in the file name) are never offered again as a resume candidate, a
  fork source or a name conflict.
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
./gradlew build      # compile and package every registered version
./gradlew test       # pure-logic unit tests (run for every registered version)
```

Stonecutter 的活动版本在 `stonecutter.gradle.kts` 中配置；构建会编译并打包**全部**已注册
版本，产物位于 `versions/<mc>/build/libs/`。
The active Stonecutter version is set in `stonecutter.gradle.kts`; the build compiles and
packages **all** registered versions.

```bash
./gradlew stonecutterSwitch   # change the active version / 切换活动版本
```

单元测试位于 `src/test/java`，只覆盖与 Minecraft 无关的纯逻辑（交易类别匹配、会话/撤销/比较、
`WorldId` 文件名、`DraftStorage` 的续接/fork/重名查找）。带条件注释的测试源码同样由 Stonecutter
按版本生成，因此每个注册版本都会跑同一套用例。
The unit tests live in `src/test/java` and only cover Minecraft-independent logic (trade category
matching, session/undo/comparison, `WorldId` file names, and `DraftStorage`'s resume/fork/conflict
lookups). Stonecutter mirrors `src/test/java` into every registered version, so each target runs the
same suite.

## Known gaps / 暂缓项

- **真半透明渲染**（规划 9.4 / Q3 / Q24 / M8）暂缓：需要按版本处理实体渲染状态，且必须在
  Sodium/Iris 上单独验证；失败时回退到现有的 Glowing 描边。
- **额外版本 1.21.1 / 1.21.10 / 1.19.4** 暂缓：这三个目标的 API 漂移面很大（`Holder<VillagerProfession>`、
  `ResourceLocation`/`Identifier`、`MerchantOffer` 成本类型、 Enchantment 访问器、客户端命令、
  malilib 分支差异），需要逐版本闭环，代价高于当前收益。
- **offers-hud 准星拉包开关** 暂缓，理由见“兼容性”。
- 三项均记录在仓库外的《未完成部分实现计划》与 `HANDOVER.md` 中。
  Semi-transparent rendering and the three extra Minecraft targets are deferred; the reasoning is
  recorded in `AGENTS.md` and `HANDOVER.md`.

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
