# 平板网络实施计划

> **给 agentic workers:** REQUIRED SUB-SKILL: 使用 superpowers:subagent-driven-development 或 superpowers:executing-plans 按任务执行。步骤使用 checkbox (`- [ ]`) 追踪。

**目标:** 为义警、黑警、active 内鬼词条好人新增金币/商店/购买平板，并实现平板 GUI、聊天、紧急会议、嫌疑人、自动高亮。

**架构:** 平板系统使用独立 `tablet` 包，不扩展侦探/毒理学家/黑警已有状态。服务端世界组件持有聊天、会议、嫌疑人、冷却与移除赞成票；客户端只显示快照并发请求。经济资格按角色/词条判断，平板功能按 hotbar 0-8 实际持有判断。

**Tech Stack:** Fabric 1.21.1、Java 21、Wathe API、NoellesRoles、本地 SparkTraits、Cardinal Components API、Fabric networking v1、JUnit 5。

## 全局约束

- 后续 spec 和 plan 文档使用中文。
- 金钱、商店、购买平板只授予 `wathe:vigilante`、`noellesroles:corrupt_cop`、active `sparktraits:impostor` 三类目标。
- 持有平板本身不授予金币显示、商店访问或购买权限。
- 平板持有检测只检查 hotbar 0-8。
- UI 显示玩家游戏名，不显示 UUID。
- 连接页只区分局内/非局内，不显示死亡状态。
- 平板时钟显示每个玩家自己的客户端本地时间/时区，不使用服务器时间。
- 高亮不使用本能键；普通平板持有人高亮必须 `localPlayer.canSee(target)`。
- 嫌疑人 45s/5s 周期橙色高亮不受墙体阻挡。
- 存活平板持有人若也是嫌疑人，橙色嫌疑人高亮优先于义警蓝色平板高亮。
- 嫌疑人高亮只对平板持有人可见，不使用全局发光。
- 会议和移除嫌疑人投票只计算 Wathe 局内存活且 hotbar 有平板的玩家。
- 紧急会议全局冷却 60 秒，会议投票默认 100 秒；进行中不能再次开会。
- `/sparkstrength:emergencyMeetingChances <num>` 设置每人每局可召开次数，默认 1。
- `/sparkstrength:voteTime <sec>` 和兼容别名 `/sparkstength:voteTime <sec>` 设置之后会议的投票时间。
- 会议最高票并列、全弃票或无有效票时不新增嫌疑人。
- 嫌疑人移除是持续赞成票，达到当前合格投票人的 2/3 即移除；移除后可再次成为嫌疑人。
- 不改变无关角色、无关词条、SparkTraits 词条分发或 NoellesRoles 原有角色逻辑。
- 新增非平凡代码注释使用中英双语。

---

## 文件结构

- 修改 `settings.gradle`：纳入同级 `../SparkTraits` included build。
- 修改 `build.gradle`、`gradle.properties`、`fabric.mod.json`：显式 SparkTraits 依赖与 metadata。
- 修改 `SparkStrengthItems.java`：注册 `sparkstrength:tablet`。
- 新增 `item/TabletItem.java`：右键请求打开/同步平板。
- 修改 `role/NoellesRoleIds.java`、`role/NoellesRoleEnhancementRules.java`、`role/NoellesRoleEnhancementService.java`：新增义警/黑警/内鬼词条经济资格和平板商店项。
- 新增 `tablet/TabletRules.java`：纯规则、常量、hotbar 检测、2/3 阈值、投票结果。
- 新增 `tablet/TabletAccess.java`：统一判断购买资格、平板持有、局内/存活、会议投票资格。
- 新增 `tablet/TabletStateService.java`：服务端动作入口、聊天、会议、嫌疑人移除、快照同步。
- 新增 `component/tablet/TabletWorldComponent.java`：服务端世界状态。
- 修改 `SparkStrengthComponents.java`、`SparkStrengthEvents.java`：注册并在回合结束/重置清理平板状态。
- 新增 `network/tablet/*.java`：C2S 请求和 S2C 快照/open 包。
- 修改 `SparkStrengthPackets.java`、`SparkStrengthClient.java`：注册网络包和客户端打开 GUI。
- 新增 `client/screen/tablet/*.java`：平板 GUI、列表、聊天输入、会议按钮、嫌疑人按钮。
- 修改 `client/role/NoellesRoleEnhancementClientHooks.java` 或新增 `client/tablet/TabletClientHighlights.java`：自动高亮。
- 修改 `assets/sparkstrength/lang/en_us.json`、`zh_cn.json`、`models/item/tablet.json`。
- 新增/修改 `src/test/java/...`：规则、资源和纯服务逻辑测试。

## 任务 1：依赖、资源、物品和经济入口

**文件:**
- 修改: `settings.gradle`
- 修改: `build.gradle`
- 修改: `gradle.properties`
- 修改: `src/main/resources/fabric.mod.json`
- 修改: `src/main/java/annina/sparkstrength/SparkStrengthItems.java`
- 新增: `src/main/java/annina/sparkstrength/item/TabletItem.java`
- 修改: `src/main/java/annina/sparkstrength/role/NoellesRoleIds.java`
- 修改: `src/main/java/annina/sparkstrength/role/NoellesRoleEnhancementRules.java`
- 修改: `src/main/java/annina/sparkstrength/role/NoellesRoleEnhancementService.java`
- 新增: `src/main/resources/assets/sparkstrength/models/item/tablet.json`
- 修改: `src/main/resources/assets/sparkstrength/lang/en_us.json`
- 修改: `src/main/resources/assets/sparkstrength/lang/zh_cn.json`
- 修改: `src/test/java/annina/sparkstrength/SparkStrengthResourceTest.java`
- 修改: `src/test/java/annina/sparkstrength/role/NoellesRoleEnhancementRulesTest.java`

**接口:**
- 产出 `SparkStrengthItems.tablet()`。
- 产出 `NoellesRoleEnhancementRules.TABLET_ENTRY_ID = "sparkstrength_tablet"`、`TABLET_PRICE = 150`。
- 产出 `NoellesRoleEnhancementService.isTabletEconomyEligible(PlayerEntity)`。

- [ ] 加入本地 SparkTraits included build 与依赖。
- [ ] 注册 `sparkstrength:tablet` 物品、模型、英文/中文本地化。
- [ ] 在商店事件中只给义警、黑警、active 内鬼词条玩家添加 150 金币平板项。
- [ ] 在金币显示和任务奖励中只扩展义警、黑警、active 内鬼词条玩家，不因持有平板解锁。
- [ ] 跑 `./gradlew test --tests annina.sparkstrength.role.NoellesRoleEnhancementRulesTest --tests annina.sparkstrength.SparkStrengthResourceTest`。

## 任务 2：平板纯规则与服务端世界状态

**文件:**
- 新增: `src/main/java/annina/sparkstrength/tablet/TabletRules.java`
- 新增: `src/main/java/annina/sparkstrength/tablet/TabletAccess.java`
- 新增: `src/main/java/annina/sparkstrength/component/tablet/TabletWorldComponent.java`
- 修改: `src/main/java/annina/sparkstrength/component/SparkStrengthComponents.java`
- 修改: `src/main/resources/fabric.mod.json`
- 新增: `src/test/java/annina/sparkstrength/tablet/TabletRulesTest.java`

**接口:**
- `TabletAccess.hasTabletInHotbar(PlayerEntity): boolean`
- `TabletAccess.isInGame(PlayerEntity): boolean`
- `TabletAccess.isAliveTabletParticipant(ServerPlayerEntity): boolean`
- `TabletRules.meetsTwoThirds(int approvals, int electorate): boolean`
- `TabletWorldComponent.KEY`

- [ ] 实现 hotbar 0-8 检测和局内/存活/投票资格判断。
- [ ] 实现聊天记录、会议状态、嫌疑人、移除赞成票、冷却结束 tick 的世界组件。
- [ ] 世界组件写入 NBT 但不靠 CCA 自动同步给所有人，后续用专用快照包同步。
- [ ] 测试 2/3 阈值、会议结果并列/弃票/唯一最高票、hotbar 常量。

## 任务 3：服务端动作与网络同步

**文件:**
- 新增: `src/main/java/annina/sparkstrength/tablet/TabletStateService.java`
- 新增: `src/main/java/annina/sparkstrength/network/tablet/TabletSnapshot.java`
- 新增: `src/main/java/annina/sparkstrength/network/tablet/OpenTabletScreenS2CPacket.java`
- 新增: `src/main/java/annina/sparkstrength/network/tablet/SyncTabletSnapshotS2CPacket.java`
- 新增: `src/main/java/annina/sparkstrength/network/tablet/RequestTabletSnapshotC2SPacket.java`
- 新增: `src/main/java/annina/sparkstrength/network/tablet/SendTabletChatC2SPacket.java`
- 新增: `src/main/java/annina/sparkstrength/network/tablet/CallTabletMeetingC2SPacket.java`
- 新增: `src/main/java/annina/sparkstrength/network/tablet/CastTabletVoteC2SPacket.java`
- 新增: `src/main/java/annina/sparkstrength/network/tablet/ConfirmTabletVoteC2SPacket.java`
- 新增: `src/main/java/annina/sparkstrength/network/tablet/ApproveSuspectRemovalC2SPacket.java`
- 修改: `src/main/java/annina/sparkstrength/network/SparkStrengthPackets.java`
- 修改: `src/main/java/annina/sparkstrength/item/TabletItem.java`
- 修改: `src/main/java/annina/sparkstrength/event/SparkStrengthEvents.java`

**接口:**
- `TabletStateService.openTablet(ServerPlayerEntity)`
- `TabletStateService.sendChat(ServerPlayerEntity, String)`
- `TabletStateService.callMeeting(ServerPlayerEntity)`
- `TabletStateService.castVote(ServerPlayerEntity, Optional<UUID>)`
- `TabletStateService.confirmVote(ServerPlayerEntity)`
- `TabletStateService.setSuspectRemovalApproval(ServerPlayerEntity, UUID, boolean)`
- `TabletStateService.syncToTabletHolders(ServerWorld)`

- [ ] 所有 C2S 在服务端重新校验 hotbar 平板、局内、存活、冷却和锁票。
- [ ] 聊天限制 120 字符，记录保留一局并在回合结束清理。
- [ ] 会议默认 100 秒，支持命令改投票时间，结束后全局 60 秒冷却。
- [ ] 嫌疑人移除达到当前合格投票人 2/3 即移除并清票。
- [ ] 回合 finalize 清空会议、嫌疑人、移除票、冷却、聊天。

## 任务 4：客户端 GUI

**文件:**
- 修改: `src/client/java/annina/sparkstrength/client/SparkStrengthClient.java`
- 新增: `src/client/java/annina/sparkstrength/client/screen/tablet/TabletScreen.java`
- 新增: `src/client/java/annina/sparkstrength/client/screen/tablet/TabletClientState.java`
- 新增: `src/client/java/annina/sparkstrength/client/screen/tablet/TabletTab.java`
- 新增: `src/client/java/annina/sparkstrength/client/screen/tablet/TabletPlayerRow.java`

**接口:**
- `TabletClientState.apply(TabletSnapshot snapshot)`
- `new TabletScreen()`

- [ ] 打开屏幕后请求快照，并接收 S2C 更新。
- [ ] 顶部显示客户端本地时间。
- [ ] 左侧四个页签：连接人数、聊天室、紧急会议、嫌疑人。
- [ ] 连接页显示头像、游戏名、局内绿色/非局内灰色，不显示生死。
- [ ] 聊天页显示本局聊天记录并可发送。
- [ ] 会议页对死亡/非局内/无平板状态置灰；可投票、改票、弃票、确认。
- [ ] 嫌疑人页显示连续赞成票并可赞成/取消。

## 任务 5：客户端自动高亮

**文件:**
- 新增: `src/client/java/annina/sparkstrength/client/tablet/TabletClientHighlights.java`
- 修改: `src/client/java/annina/sparkstrength/client/role/NoellesRoleEnhancementClientHooks.java`
- 修改: `src/client/java/annina/sparkstrength/client/SparkStrengthClient.java`

**接口:**
- `TabletClientHighlights.register()`

- [ ] 本地 hotbar 有平板才显示平板/嫌疑人高亮。
- [ ] 普通平板目标在视野内 `localPlayer.canSee(target)` 才高亮。
- [ ] 存活平板目标用义警蓝 `0x1B8AE5`。
- [ ] 存活嫌疑人每 45 秒亮 5 秒橙色，且不受墙体阻挡；若目标也是平板持有人，橙色优先。
- [ ] 不使用本能键，不设置全局 glowing。

## 任务 6：验证与收尾

**文件:**
- 修改测试资源和所有相关文件。

- [ ] 跑 `./gradlew test`。
- [ ] 跑 `./gradlew build`。
- [ ] 检查 `git diff --stat`，确认未改无关仓库。
- [ ] 提交实现。
