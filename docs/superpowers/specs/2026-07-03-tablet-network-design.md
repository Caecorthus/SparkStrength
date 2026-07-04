# 平板网络设计规格

日期：2026-07-03
项目：SparkStrength

## 目标

为以下三类玩家新增金币/商店入口和平板购买资格：

- Wathe 义警（`wathe:vigilante`）
- NoellesRoles 黑警（`noellesroles:corrupt_cop`）
- 拥有 active 内鬼词条的好人（`sparktraits:impostor`）

这些资格只负责经济和购买入口。平板运行时功能以玩家 hotbar 0-8 是否实际持有 `sparkstrength:tablet` 为准。

## 硬规则

- 金钱显示、商店访问、购买平板只给义警、黑警、active 内鬼词条好人。
- 单纯持有平板不会获得金钱显示、商店访问或购买资格。
- 平板检测只检查 hotbar 0-8。
- 平板 UI 显示 Minecraft 游戏名，不显示 UUID。
- 平板顶部时钟显示客户端本地时间，不使用服务器时间。
- 连接页只显示“局内/局外”，不暴露死亡状态。
- 平板高亮不使用本能键，不使用全局 glowing。
- 普通平板持有人高亮需要 `localPlayer.canSee(target)`。
- 嫌疑人周期高亮每 45 秒亮 5 秒，橙色，且不受墙体阻挡。
- 存活平板持有人若也是嫌疑人，橙色嫌疑人高亮优先于义警蓝色平板高亮。
- 会议和嫌疑人移除投票只计算“Wathe 局内、存活、hotbar 持有平板”的玩家。
- 聊天记录保留一局，局结束后自动清理。
- 新增非平凡代码注释使用中英双语。

## 经济资格

满足以下任一条件的玩家获得金钱显示、商店入口和平板购买资格：

- 角色是 `wathe:vigilante`
- 角色是 `noellesroles:corrupt_cop`
- 当前 active traits 包含 `sparktraits:impostor`

平板价格为 150 金币，购买数量限制为 1。

## 平板物品

新增 `sparkstrength:tablet`。

- 右键请求服务端打开平板 GUI。
- 对其他玩家不可见，沿用 NoellesRoles 隐藏装备过滤路径。
- 所有 C2S 动作都由服务端重新校验平板持有、局内状态、存活状态、冷却和投票锁定。

## GUI

平板 GUI 包含顶部本地时钟和左侧四个页签：

- 连接人数
- 聊天室
- 紧急会议
- 嫌疑人

### 连接人数

列出 hotbar 0-8 持有平板的玩家。

- 显示头像和游戏名。
- 局内玩家为绿色边框。
- 局外玩家为灰色边框。
- 不显示死亡状态。

### 聊天室

- 只要持有平板即可发送聊天，不要求局内或存活。
- 空消息忽略。
- 消息裁剪到 120 字符。
- 聊天记录保留一局，回合结束清理。
- 客户端聊天输入框保留未发送草稿。
- 聊天输入框聚焦时拦截移动键，避免 WASD 等按键移动玩家。
- Enter / 小键盘 Enter 发送消息。

### 紧急会议

会议参与者必须同时满足：

- Wathe 局内
- 存活
- hotbar 0-8 持有平板

会议规则：

- 默认每人每局可召开 1 次。
- `/sparkstrength:emergencyMeetingChances <num>` 设置每人每局可召开次数。
- 会议全局冷却 60 秒。
- 默认投票时间 100 秒。
- `/sparkstrength:voteTime <sec>` 设置之后会议的投票时间。
- `/sparkstength:voteTime <sec>` 也注册为兼容别名。
- 会议进行中不能再次召开会议。
- 可投票、改票、弃票、确认。
- 确认后本次会议不可再改票。
- 会议在时间结束或所有当前合格参与者确认后结束。

结果规则：

- 全弃票、无有效票或最高票并列时不新增嫌疑人。
- 唯一最高票玩家被加入嫌疑人列表。
- 已在嫌疑人列表的玩家不会成为后续会议可选目标，直到被移除。

### 嫌疑人

- 列出所有当前嫌疑人。
- 显示头像、游戏名和建议撤销票数。
- 移除投票是持续状态，不是限时会议。
- 当前合格投票人的 2/3 建议撤销后，嫌疑人被移除。
- 移除后清空该嫌疑人的移除票，并允许未来再次成为会议目标。

## 高亮

### 平板持有人高亮

观看者：

- 本地玩家 hotbar 0-8 持有平板。

目标：

- 存活且局内。
- hotbar 0-8 持有平板。
- 本地玩家能看见目标。

渲染：

- 义警蓝 `0x1B8AE5`。
- 若目标也是嫌疑人，则改用嫌疑人橙色。
- 不使用本能键。

### 嫌疑人周期高亮

观看者：

- 本地玩家 hotbar 0-8 持有平板。

目标：

- 存活且局内。
- 当前在嫌疑人列表中。

渲染：

- 每 45 秒亮 5 秒。
- 橙色 `0xFF8C00`。
- 不受墙体阻挡。
- 不使用本能键。
- 不设置全局 glowing。

## 数据所有权

服务端世界组件持有：

- 聊天记录
- 当前会议状态
- 全局会议冷却
- 每人本局召开会议次数
- 当前嫌疑人列表
- 嫌疑人移除建议票
- 可配置会议投票时长

客户端只持有：

- 当前快照
- 未发送聊天草稿
- GUI 页签状态

局结束时清理聊天、会议、冷却、嫌疑人、移除票和本局会议次数；配置项不随局清理。

## 网络包

C2S：

- 请求快照
- 发送聊天
- 召开会议
- 投票/改票/弃票
- 确认投票
- 建议撤销/取消建议撤销嫌疑人

S2C：

- 打开平板屏幕
- 同步平板快照

## 验证

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home sh ./gradlew clean compileClientJava --no-daemon --no-parallel`
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home sh ./gradlew :test --no-daemon --no-parallel`
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home sh ./gradlew :build --no-daemon --no-parallel`
