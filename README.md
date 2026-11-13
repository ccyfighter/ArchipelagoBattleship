# 群岛海战棋 · Archipelago Battleship

Android 14+ 原生策略战棋（Kotlin + Jetpack Compose Material 3 + Coroutines/StateFlow +
kotlinx.serialization）。单机 AI 对战 / 双机面对面局域网联机，纯本地、无公网服务器、
不依赖 Google Play Services。

> **v8 更新（当前版本）**
> 1. 启动图标按手绘草图重绘为「三栖合击徽章」：正中战列舰舰首主炮侧向开火、
>    左上舰载机俯冲投弹、右下潜艇发射鱼雷，机头统一朝内/朝前；同时补上
>    Android 13+ 主题单色图标 `ic_launcher_monochrome.xml`。原创矢量，无版权风险。
> 2. 首页封面同步改成同一枚徽章，封面下方可切换底图：**雷达图 / 棋盘**。
> 3. 联机房间规则明确为 **房主退出 = 解散房间**（不做房主迁移）：新增
>    `Packet.RoomClosed`，房主关闭 Socket 前可靠下发；客机收到关房包或任何断链
>    都会清理状态、回首页并弹出说明（可一键「我来做房主」开新房）；房主侧客机
>    掉线则房间保持开放等待重连；NSD `onServiceLost` 自动移除已关闭的房间。
>    不做迁移的原因：权威引擎与双方隐藏信息只在房主内存，客机仅有脱敏快照，
>    迁移必须泄露全部情报，且广播 / 二维码 / 端口都绑定房主设备。
>
> ⚠ 联机两台手机务必装**同一版本** APK，v8 协议新增 `room_closed` 帧。

---

## 一、第一次：还原工程 + 推送 GitHub

### 第 0 步：GitHub 网页新建**空**仓库
名字建议 `ArchipelagoBattleship`，**不要**勾选 Add a README / .gitignore / license。
记下地址，形如 `https://github.com/你的用户名/ArchipelagoBattleship.git`

### 第 1 步：还原工程目录
在本文件夹（`archipelago-battleship`）里打开 PowerShell，逐行执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\restore-project.ps1
```

脚本会：
- 递归遍历所有 `*.txt`，剥掉末尾 `.txt`，保留完整层级
  （如 `.github/workflows/android-build.yml.txt` → `.github/workflows/android-build.yml`）；
- 写出 `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.properties`；
- 如果本机装了 `gradle`，顺手生成 `gradle-wrapper.jar`；没装也没关系，
  GitHub Actions 会自己生成（见 CI 的 Generate Gradle Wrapper 步骤）。

脚本只覆盖、不删除，`.git` 会被自动跳过，可反复运行。

### 第 2 步：初始化并推送（Git Bash 或 PowerShell 都行）

```bash
cd "C:/Users/DELL/Desktop/Application/archipelago-battleship"

git init
git config user.name "你的GitHub用户名"
git config user.email "你注册GitHub的邮箱"

git add -A
git commit -m "init: 群岛海战棋 v1.0.0"

git branch -M main
git remote add origin https://github.com/你的用户名/ArchipelagoBattleship.git
git push -u origin main
```

> 首次 push 的「密码」要填 GitHub Personal Access Token（网页 → 头像 → Settings →
> Developer settings → Personal access tokens → Tokens (classic) → 勾 repo 生成），
> 不是账号密码。Windows 的凭据管理器之后会记住。
>
> ⚠ 不要用 GitHub 网页拖拽上传：`.github/` 和 `.gitignore` 这类以点开头的文件会被忽略，
> Actions 页签就找不到工作流了。一定用 git 命令行。

### 第 3 步：等构建产物
push 到 `main` 会自动触发 `Android Build`；也可以到仓库 → Actions → Android Build →
Run workflow 手动触发。绿勾后下载 Artifact `archipelago-battleship-debug`，
里面就是 `app-debug.apk`，传到手机安装。

---

## 二、后续迭代怎么更新

**约定：我每次给的都是整份文件，不会说"把第 X 行改成 Y"。**

1. 用我给的新 `.txt` **整份覆盖**本文件夹里的同名 `.txt`（Ctrl+A 全选再粘贴）。
2. 重跑一次 `.\restore-project.ps1`（或把新内容直接粘进已还原的同名文件）。
3. 推送：

```bash
git add -A
git commit -m "update: 修复xxx"
git push
```

---

## 三、硬性规则（踩过坑）

1. 所有 XML 第一行必须是 `<?xml version="1.0" encoding="utf-8"?>`，前面不能有空行/注释。
2. `.github/workflows/android-build.yml` 第一行必须是 `name: Android Build`。
3. 源码是 UTF-8 无 BOM；脚本 `restore-project.ps1` 全篇纯 ASCII 注释，
   PowerShell 5.1 按 GBK 读带中文注释的 .ps1 会直接语法错。
4. `.gitignore` 里忽略了 `*.txt` 和 `gradle/wrapper/gradle-wrapper.jar`（二进制没法放进 txt），
   这是正常的：CI 自己装 Gradle 8.7 并生成 wrapper。
5. 如果想要本机编译，需要自己装 JDK 17 + Android SDK（platforms;android-34 /
   build-tools;34.0.0）+ Gradle 8.7，然后 `gradle wrapper` 再 `./gradlew assembleDebug`。

---

## 四、版本与目录

AGP 8.5.2 / Gradle 8.7 / JDK 17 / Kotlin 1.9.24 / Compose Compiler 1.5.14 /
Compose BOM 2024.06.00 / compileSdk 34 / targetSdk 34 / minSdk 26 /
ZXing core 3.5.3 / CameraX 1.3.4 / kotlinx-serialization-json 1.6.3。

> 依赖版本是刻意锁在 2024 年中的组合：compileSdk 34 下，太新的 AndroidX
> （core 1.15 / Compose 1.7 等）会因为 `minCompileSdk=35` 直接让
> `checkDebugAarMetadata` 报错。

```
根目录
  settings.gradle.kts / build.gradle.kts / gradle.properties
  gradle/wrapper/gradle-wrapper.properties
  .github/workflows/android-build.yml
  .gitignore / README.md
  restore-project.ps1            ← 工具脚本，不参与编译
app/
  build.gradle.kts / proguard-rules.pro
  src/main/AndroidManifest.xml
  src/main/res/values/{strings,colors,themes}.xml、values-night/themes.xml
  src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml
  src/main/res/mipmap-anydpi-v26/ic_launcher{,_round}.xml
  src/main/java/com/archipelago/battleship/
    MainActivity.kt
    core/    AppTheme.kt          三套主题配色 + Material3 主题
             BattleScene.kt      首页封面三栖合击徽章（雷达图 / 棋盘底图可切）
             ShipGlyph.kt         原创舰种矢量标识（Canvas）
             BoardCanvas.kt       坐标纸 / 声呐雷达棋盘、命中叉与水花圈
    domain/  model/GameModels.kt 全部数据类、枚举、舰船目录、GameAction、GameView
             engine/Rules.kt     地形、布阵校验、技能参数表（纯 Kotlin）
             engine/GameEngine.kt 房间状态机 + 战斗结算（唯一权威，纯 Kotlin）
             engine/AiEngine.kt   Hunt & Target 双状态机 AI（纯 Kotlin）
    data/    net/Packets.kt      行分隔 JSON 协议
             net/LanLink.kt      Socket 长连接端点 + 本机 IPv4 探测
             net/NsdDiscovery.kt NsdManager mDNS 注册 / 发现 / 解析
             qr/QrCodec.kt       ZXing 二维码生成 + Y 平面解码
    ui/      GameViewModel.kt    MVVM / UDF / 三种模式编排 / 180s 计时
             AppRoot.kt          路由 + 权限申请 + 结算弹窗
             components/UiBits.kt
             screens/{Home,Lobby,Fleet,Battle,Scanner}Screen.kt
```

---

## 五、玩法与交互流

### 房间状态机
- `ROOM_LOBBY`：文字聊天 + 快捷战术表情；房主可切 10×10 快棋 / 12×12 标准（默认） / 14×14 大战场，
  12×12 与 14×14 显示【随机群岛】勾选框（默认勾选有山，取消 = 纯海），改动实时同步客机；
  双方各自选船布阵 → 准备 → 房主点「开始战斗」。
- `IN_BATTLE`：聊天输入框与发送**立即禁用/隐藏**；棋盘 + 战况播报 + 180s 倒计时。
- `POST_BATTLE_LOBBY`：任一方全灭弹结算（双方存活/击沉明细、回合数、MVP）；
  点「返回房间」→ **不断开连接、不退出房间**，回到大厅，聊天重新解锁，
  房主可重调地图 / 山地，双方可换船重开下一局。

### 舰队（严格 5+4+3+2+1，共 15 格）
| 格数 | 二选一 |
| --- | --- |
| 5 | 航空母舰 / 战列舰 |
| 4 | 航空巡洋舰 / 火力巡洋舰 |
| 3 | 潜艇 / 防空巡洋舰 |
| 2 | 驱逐舰 / 反潜舰 |
| 1 | 高速炮艇（必选） |

全部技能（侦察机 CD3/全局5次、绝境副炮、航母本体点防空、主炮齐射 CD3、
鱼雷机空袭（3 格射程 / 单点爆炸）CD3/全局2次、高能燃烧弹全局2发+AA瘫痪、潜航鱼雷+潜望镜一击必沉+强制上浮、
区域防空网、紧急规避机动全局1次、深水炸弹炮、主动水听声呐、灵活规避、
呼叫陆航空袭延时1回合且无视一切AA）都在 `domain/engine/GameEngine.kt` 中实现，
编成界面会展示当前选中舰种的完整数值说明。
侦察机 / 水听声呐 / 主炮齐射 / 鱼雷机空袭自第 2 回合起可用，燃烧弹第 3 回合起，陆航空袭第 4 回合起。
侦察机扫描成功后，敌方棋盘会把扫过的十字 5 格标成「已侦察」放大镜（只显示扫描区，不显示舰船坐标）。

### 回合流
回合初自动结算延时空袭 / 刷新 CD / 解除 AA 瘫痪 / 更新潜艇上浮（含【上浮暴露复核】）→
阶段一（可选 1 次战术行动，或显式点「进入阶段二」）→
阶段二（攻击名额 = min(存活舰船数, `Rules.BASE_ATTACKS_PER_TURN`，默认 3)，每舰 1 次，随时可结束回合）
→ 180s 超时按剩余未行动舰船数自动随机常规炮击并结束回合。
所有操作都是「选船 → 选目标（可反复改） → 点确认」，不自动切船、不自动释放技能、不自动跳阶段；
本回合内可「撤销 / 重做」，换手自动清空撤销栈。
除十字 5 格范围伤害外，单点攻击禁止选已攻击过的格子（候选格高亮 + 引擎二次校验）。
棋盘采用「底色 + 形状 + 颜色」三重编码：青绿四角括号=可攻击、灰斜纹=已打过、
琥珀放大镜=侦察机已扫描、蓝空心圆环=未命中、红圆白叉=命中、深红整船残骸框=击沉、
紫菱形盾=拦截/免疫/阻挡、橙色呼吸双框=最近一击；棋盘下方有 9 项矢量图例，
顶部另有最近 3 条战果彩色横幅。

### 【潜望镜一击必沉】（潜艇专属）
水下潜艇的中心第 2 格是唯一暴露点。任何能命中该格的攻击（常规炮击 / 舰载机炸弹 /
深水炸弹 / 鱼雷）一旦命中，直接判整艇沉没；战报按统一格式只播报坐标与结果（击沉才公开舰种）；
沉船守卫随即禁止它再开火或释放技能，不会再出现「带伤发射鱼雷、等上浮才沉」。
只有落在首尾两格的 MISS 才走下面的【上浮暴露复核】。

### 【上浮暴露复核】（潜艇专属，解决"打过的格子锁死"）
水下潜艇首尾两格被常规炮击时按规则反馈「未命中（水花）」，同时坐标会进入去重集合。
若潜艇此后强制上浮，这些格子本应可被击中，却因去重无法再打。处理方式是：
**上浮那一刻自动把此前「因潜望镜隐蔽而报 MISS 的单点炮击」复核为命中**，
直接对潜艇造成 1 格伤害、棋盘上对应 ○ 自动变 X、战况只播报「该坐标的既有攻击复核为命中」，
命中按原攻击者归因（MVP 统计不漏）。玩家不额外获得攻击机会，坐标依旧保持去重，
也不会因为"哪些格子被解锁"而泄露潜艇位置。
只匹配落在潜艇首尾格、且当时「没找到船」的单点 MISS；空海水花、十字范围伤害、
「该舰格已损毁」、侦察 / 深弹 / AA 拦截等记录一律不参与，其他舰船打击逻辑零改动。
复核后记录由 MISS 改为 HIT，因此后续再次上浮不会重复结算。

### 鱼雷机空袭（航空巡洋舰）
阶段二技能，第 2 回合起 / CD3 / 全局 2 次：先点投雷起点，再点与其同行或同列的一格指定航向
（第二点仅定方向，不要求该格未被攻击过）。鱼雷沿航向最多推进 3 格，**只在一处爆炸、只结算一次伤害**：
① 碰到第一艘未沉敌舰 → 该格爆炸；② 前方是山地 → 在撞山前一格爆炸（起点即山地则空袭无效）；
③ 都没遇到 → 在射程尽头一格爆炸。伤害判定与潜航鱼雷完全相同（同为 `AttackKind.TORPEDO`，
可命中水下潜艇、不受炮艇「灵活规避」影响）；沿途不写 ShotRecord、不画轨迹，
棋盘只在爆炸点留下一个命中 / 未命中标记，中间格以后仍可用火炮正常打击。
**AA 圈外视距打击**：防空判定只检查「投雷起点」这一个部署点 —— 起点在敌方航母 /
防空巡洋舰防空圈外，飞机即可投雷，即使爆炸点落在防空舰身上也照常结算；
只有起点落进防空圈才被击落、整次空袭失效。爆炸点属于范围伤害，不写入坐标去重。

### 沉没舰船
已沉没舰船在所有行动入口（开火 / 技能 / AI / 超时托管）统一被拦截，且会给操作者一条
仅自己可见的 `【已沉没】… 无法再开火或释放技能` 提示；界面同步移除其选船与技能按钮。敌方棋盘上仍会保留
已沉舰的矢量图标（这是"战果展示"，不是该舰仍可行动）。

### 地形
12×12 / 14×14 且勾选山地时生成 3~8 座随机群岛（单岛 1~6 格，形状随机，可贴边角，
岛间留 1 格，总面积 ≤ 20%，双方使用同一份地形因此无需对称）；
山地不可部署 / 不可攻击 / 阻挡鱼雷；紧贴 1 圈为近岸浅滩，
3~5 格大舰与潜艇禁入，1~2 格小舰（含炮艇）可入。

### 联机
- 房主：`ServerSocket` 监听随机端口 + `NsdManager` 广播 `_archipelago._tcp`，
  大厅直接展示含 `IP:Port + 房间密钥` 的二维码。
- 客机：扫码（CameraX + 纯 ZXing）/ 搜索附近房间 / 手动输入 `IP:端口:密钥`，
  三路都走同一条 Socket 长连接。
- 权威状态在房主，客机只发 `GameAction`、收脱敏 `GameView`（敌方未沉舰船不下发），
  避免隐藏信息泄露与状态不同步。
- 权限：`NEARBY_WIFI_DEVICES`（`neverForLocation`）、`ACCESS_NETWORK_STATE`、
  `ACCESS_WIFI_STATE`、`INTERNET`、`CAMERA`，运行时动态申请。

---

## 六、如实说明的工程取舍

1. **房主是权威端**：客机的布阵会发给房主（面对面娱乐对局，不涉及公网与隐私数据）。
   换来的是零状态不同步、聊天/倒计时/结算全部由一处裁决。
2. **回合编号**：`round` 为「双方各行动一次」的配对回合；A 先手、B 后手，
   B 结束后 `round+1`。「第 2/3/4 回合起可用」以此为基准，双方公平。
3. **AI**：单机 AI 使用 Hunt & Target 决定攻击坐标，攻击方式以常规炮击为主，
   不主动释放全部花式技能；**人方**可完整测试自己所有舰船技能。
4. **扫码解码**：CameraX 取 Y 平面 + ZXing `MultiFormatReader`，按
   `imageInfo.rotationDegrees` 旋转后解码；极端机型/角度识别失败时，
   请用「搜索附近房间」或「手动输入 IP:端口:密钥」兜底。
5. **mDNS 服务类型**用 `_archipelago._tcp`（部分 ROM 对 NSD 有限制），
   因此二维码与手动直连是并列的一等公民通道，不依赖 NSD 成败。
6. 三套主题（坐标纸 / 情报室 / 声呐雷达）的扫描线、折痕、定位十字、同心环、
   命中叉与水花圈都是原创 Canvas 矢量绘制，不依赖任何图片、字体或第三方素材。

---

## 七、v3 视觉更新

- **坐标标尺带**：棋盘外圈改为深墨蓝坐标框，横向 A… / 纵向 1… 用高对比亮字；
  选中目标时对应行列标签同步橙色高亮，与页面背景、水面均明显区分。
- **判定三重编码**（底色 + 形状 + 颜色）：可攻击 = 青绿四角瞄准括号；已打过 = 灰斜纹；
  未命中 = 蓝空心圆环 + 水花；命中 = 红实心圆 + 白叉；击沉 = 整艘船深红残骸框 +
  白圆红叉 + S 角标；拦截 / 免疫 / 阻挡 = 紫斜纹 + 菱形盾；最近一击 = 橙色呼吸双框。
  敌方棋盘仍只下发已沉舰船，整船高亮不会泄露未沉舰位置。
- **棋盘图例**：棋盘下方 9 项矢量图例，画法与棋盘完全一致，所见即所得。
- **界面**：统一圆角描边卡片、状态胶囊、序号式首页菜单卡片、全局海图网格底纹、
  战报色条与日志色条；两套主题（机舱坐标纸 / 深海声呐）同步调整配色。

---

## 八、v4 侦察可视化 + 鱼雷 / 潜艇判定修订

1. **侦察机扫描区**：`Board.reconCells` 记录扫描成功的十字 5 格，敌方棋盘用琥珀底 +
   放大镜角标标出「已侦察」；图例同步新增该项。只显示扫描区，不显示舰船坐标；
   被 AA 击落不留痕；换局由 `Rules.buildBoard` 清空，撤销 / 重做随状态快照回退。
2. **鱼雷机单点爆炸**：不再沿 3 格逐格判定、不再画航线预览。沿直线找爆炸点
   （碰到敌舰 / 撞山前一格 / 射程尽头），只在该点用 `AttackKind.TORPEDO` 结算一次，
   与潜航鱼雷同判定（可命中水下潜艇）；沿途无 ShotRecord、无轨迹。
   战机当时仍受整条航线 AA 拦截（**v6 起改为只判投雷起点**）；第二点选方向只高亮射程内 2 格，
   不铺满整行整列。
3. **潜望镜一击必沉**：水下潜艇中心第 2 格被任何可命中它的攻击打中即整艇沉没，
   立即播报并触发沉船守卫，不会再带伤发射鱼雷、也不会等强制上浮复核才沉。
   首尾格 MISS 的上浮复核逻辑保留不变，其他舰船结算零改动。

---

## 九、v5 联机可靠性修复（客机进房后无法操作）

### 根因
房主把战况推给客机的 `publish()` 运行在主线程，而 `LanEndpoint.send()` 直接在该线程
写 `Socket`。Android StrictMode 会在主线程网络 IO 上抛 `NetworkOnMainThreadException`，
异常被 `send()` 捕获后误报「连接已断开」，于是：客机收到了 `Assign`（显示"已加入房间"），
却永远收不到第一份 `GameView` → 大厅只剩「正在同步房间…」，没有任何可点的按钮。
另外还有三处隐患会放大这个问题：NSD 服务名带系统后缀导致密钥校验失败后静默无响应、
跨线程各自 `_ui.value = _ui.value.copy(...)` 造成状态丢失、首帧丢失后没有任何补发机制。

### 修改内容
1. **单写线程**：`LanEndpoint` 内部用专用 `newSingleThreadExecutor` 串行投递所有帧，
   任意线程都能安全 `send()`；顺带保证 `Assign` 一定先于补发的 `State`。监听端支持客机断线重连，
   旧读线程用 generation 标记，不会误报"对端已断开"。
2. **握手即带状态**：`Packet.Assign` 新增 `view` 字段，房主在握手应答里直接下发第一份完整
   `GameView`；新增 `Packet.Sync`，客机可主动索要多轮全量状态（首帧丢失后最多自动重试 6 次），
   大厅也加了「重新向房主同步战况」按钮。
3. **身份与状态原子化**：`role / mySide / roomKey / joinKey / guestConnected` 改为 ViewModel
   成员变量，不再依赖易被覆盖的 UiState；所有 UI 写入走 `updateUi` 原子读-改-写，
   网络回调统一切主线程。房主收到任意客机 `Action` 都必回一份 `State`
   （哪怕动作被规则拒绝，客机也能看到提示日志）。
4. **失败可见**：`Wire.decodeDetailed` 把解析/序列化异常原因报出来，显示在首页/大厅的
   「联机诊断：…」和 Toast 中，不再静默丢帧；密钥不匹配、房主拒绝加入都回明确原因；
   NSD 解析出的房间密钥用 6 位数字正则提取，避开系统加的 " (2)" 后缀。
5. **体验兜底**：客机连接期间首页显示进度与「取消连接」；无战况时大厅显示身份/席位、
   重新同步按钮与排障清单；房主离开房间会尝试发 `Bye` 并断连（客机至少会收到断连提示）；
   180 秒超时托管改为按当前行动方执行，
   客机回合挂机也不会卡死房主。

### 双机验证步骤（两台手机务必装**同一版本** APK）
1. 两机连到同一 Wi-Fi，或 A 开热点给 B（热点场景优先用二维码，IP 以房主界面显示为准）。
2. A：创建房间（房主）→ 显示二维码与「房间 IP:端口 · 密钥」。
3. B：扫码加入 / 搜索附近房间 / 手动输入 `IP:端口:密钥`。
4. **预期**：B 立刻进入「战前准备大厅 · ROOM_LOBBY」，能看到地图信息、双方准备状态、
   舰队编成、「选船 / 布阵」「准备完毕」按钮与聊天框；A 端连接文字变成「客机已加入 · 初始战况已下发」。
5. B 点「选船 / 布阵」→「准备完毕」，A 端应实时看到「客机 ✔已准备」；A 也准备后点
   「开始战斗」，两端同时切到棋盘；B 开火后 A 端立刻看到战报。
6. 若 B 停在「正在同步房间战况…」：先看连接文字里的「联机诊断：…」，点「重新向房主同步战况」；
   仍未恢复则让 A 退出重开房间、重新扫码，并确认两机 APK 版本一致、没有隔离子网 / 访客网络隔离。

---

## 十、v6 鱼雷轨迹与战报情报修订

### 1. 潜航鱼雷改单点爆炸（解决「一串水花暴露潜艇」）
- 玩家点的同行 / 同列格**只用于定航向**，鱼雷固定最多推进 4 格；爆炸点按
  ① 碰到的第一艘未沉敌舰 → ② 前方是山地时取撞山前一格 → ③ 射程尽头 依次确定。
- 沿途**不写 `ShotRecord`、不写 `attackedPoints`、不画航线**，棋盘只留爆炸点一个标记；
  路径中间的格子此后仍可被火炮正常打击，爆炸点本身也不锁坐标。
- 发射日志不再播报潜艇中心坐标（旧版 `自 X 沿直线发射` 等于直接交出潜望镜位置），
  也不再逐格播报「鱼雷被山地阻挡 / 在已攻击格处停止」。
- **射后强制上浮、潜望镜一击必沉、首尾格【上浮暴露复核】全部不变** —— 坐标不再暴露，
  强制上浮重新成为有意义的代价。

### 2. 舰载鱼雷机：AA 圈外视距打击
- 射程保持「投雷起点 + 航向 2 格 = 3 格」不变，单点爆炸 / 不留轨迹 / 中间格可再打与潜艇一致。
- 防空拦截由 `intercepted(board, line)`（整条航线）改为 `intercepted(board, listOf(start))`
  （**只判投雷起点**）：起点在敌方航母 / 防空巡洋舰防空圈外即可投雷，
  即使爆炸点落在防空舰身上也照常结算；只有起点落进防空圈，战机才被击落、空袭失效。

### 3. 战报情报脱敏
- 命中一律只播报 `击中`，不再出现 `命中敌方 航空母舰`；**只有击沉才公开舰种**
  （含 `命中【潜望镜】…整艇直接沉没`）。
- `viewFor` 下发的 `ShotRecord.shipType`（被击中舰种）/ `attacker`（开火舰种）一律置空，
  避免日志之外的数据通道继续泄露；engine 内部 `state` 保持完整，【上浮暴露复核】不受影响。

### 4. UI
删除 `BoardCanvas.pathCells` 参数与航线 / 箭头绘制；`BattleScreen` 删除 `torpedoPath` 预览，
只保留定方向点的高亮框；潜艇候选格不再排除 `attackedPoints`（鱼雷会穿过已打过的格子）。

### 验证要点
潜艇射完鱼雷后敌方棋盘**只应新增 1 个标记**（爆炸点），不再出现连成直线的一串水花；
爆炸点与潜艇中心之间的格子之后仍可用火炮打击；从防空圈外投雷能命中防空巡洋舰；
任何未击沉的命中日志都只显示「击中」，击沉才出现舰种。


---

## 十一、v7 情报脱敏 / 投降 / 返回键 / 三套主题与封面

### 1. 战报只报「坐标 + 结果」

**问题**：播报「对方发射鱼雷 / 航母轰炸机 / 陆航空袭」等于报点 —— 只要知道鱼雷出击且在
无山海域打空，对手就能从炸点沿四个方向反推 4 格外的发射源，再用深弹一击摧毁潜艇；
「命中敌方 X」、全局广播的舰队编成与存活舰种同样在泄露情报。

- 引擎新增唯一公开出口 `reportAttack()`，格式固定为
  `房主 于 G7 发动攻击：击中 / 未命中 / 攻击被拦截 / 攻击无效 / 击沉敌方 X`。
  不出现鱼雷、舰载机、陆航、主炮齐射等攻击方式，不出现开火舰种；**只有击沉才公开舰种**。
- `LogEntry` 新增 `owner: Side?`，`logOwn()` 写仅自己可见的反馈，`viewFor()` 按收件方过滤：
  校验失败、侦察 / 声呐结果、发射细节、强制上浮预告、沉船提示全部私有化，不再送给对手。
- 潜航鱼雷 / 鱼雷机 / 燃烧弹的「发射」播报不再全局广播；延时打击只保留中性的
  「已标记 X，该区域攻击将在其下一回合生效」；战报标签 `防空拦截 / 技能` 改为
  `未奏效 / 行动`，避免标签反过来泄露方式。
- **数据通道同步收口**：`viewFor` 不再下发敌方 `hostFleet / guestFleet`（占位），
  `enemyAlive` 置空并新增 `enemyAliveCount` 只给数量；私有日志条目直接过滤；
  `ShotRecord.shipType / attacker` 继续置空。engine 内部 `state` 保持完整，
  【上浮暴露复核】等逻辑零影响。
- 仍公开的：攻击坐标、结果、击沉舰种、回合 / 阶段 / 准备 / 聊天、结算战果（打完才公开）。

### 2. 投降（单机 / 联机都可用）

- 新增 `GameAction.Resign` → 引擎 `resign(side)` 立即判对方获胜，写
  `BattleResult(reason = RESIGN, resigned = side)`，并停止计时器与 AI 回合循环。
- 战斗页新增「投降 · 结束本局」按钮，点击弹 `AlertDialog` **二次确认**；
  联机客机的动作经 `Packet.Action` 交给房主权威结算再回推状态，单机直接结算。
- 结算弹窗区分「全灭」与「投降 / 被投降」文案；返回房间保持连接、解锁聊天、直接开下一局。

### 3. 全页面返回键（AppRoot 统一 BackHandler）

| 页面 | 系统返回键行为 |
|---|---|
| HOME | 系统默认退出 App |
| SCANNER | 回首页（关闭扫码，不断开房间） |
| FLEET | 回大厅 |
| LOBBY | 弹「确认离开房间」二次确认 |
| BATTLE | 弹「离开对局」：投降 / 返回首页 / 继续战斗 |
| 结算弹窗 | 等同「返回房间」，不再吞掉返回键 |

大厅的「离开房间」「返回首页」按钮与系统返回键共用同一个确认框（`requestLeaveRoom`）。
布阵页保存后返回大厅、扫码页取消返回首页的原有路径保持不变并逐一复核。

### 4. 三套原创主题 + 首页封面 + 应用图标

- 新增 `AppThemeMode.WARROOM` 战时情报室：墨绿呢面作战桌 + 黄铜标尺 + 暖色台灯光；
  与 `PAPER` 机舱手绘坐标纸、`SONAR` 深海战术声呐组成三选一，
  三套都作用于界面、全局底纹（方格纸折痕 / 情报室定位十字 / 雷达同心环）与棋盘配色。
- 新增 `core/BattleScene.kt`：首页封面是一张海图作战态势图，同框呈现
  **战列舰主炮开火**（弹道虚线 + 命中爆花）、**舰载机投弹**（下落虚线 + 两枚炸弹）、
  **潜艇射鱼雷**（虚线航迹 + 尾流气泡），另有罗盘玫瑰、群岛剪影、水面线、
  坐标刻度、四角定位角与缓慢旋转的扫描射线。
- `ic_launcher_foreground.xml` 重绘成同一构图的简化图标。
- **版权说明**：以上全部为原创几何矢量图形，不引用任何图片、字体、影视 / 游戏素材；
  README 与源码注释里原有的「《战舰世界》风格」措辞一并改为「原创舰种矢量标识」。

### 改动文件
`GameModels.kt`、`GameEngine.kt`、`GameViewModel.kt`、`AppRoot.kt`、`BattleScreen.kt`、
`HomeScreen.kt`、`LobbyScreen.kt`、`UiBits.kt`、`AppTheme.kt`、新增 `BattleScene.kt`、
`ic_launcher_foreground.xml`、`ShipGlyph.kt`（仅注释）、`README.md`。

### 验证要点
- 潜艇射完鱼雷，战报只出现一条「某方 于 炸点坐标 发动攻击：…」，看不到「鱼雷」二字，
  也看不到潜艇中心坐标；对手无法从播报反推发射源。
- 双方在大厅只能看到自己的舰队编成，战斗中「敌方」状态只显示剩余数量。
- 任一方的战报日志里都不出现对方的侦察 / 声呐结果或技能发射细节。
- 单机与联机点「投降」→ 二次确认 → 立即结算；客机投降后房主同步看到结算弹窗。
- 扫码页、布阵页、大厅、战斗中、结算弹窗逐一按系统返回键，行为符合上表，不会误退出。
- 首页三套主题切换后，界面、底纹、棋盘、封面配色同步变化。

> 协议提示：本版新增 `GameAction.Resign`、`LogEntry.owner`、`GameView.enemyAliveCount`、
> `BattleResult.reason / resigned`（新增字段均带默认值）。**两台手机务必装同一版本 APK**，
> 混装旧版会因无法识别 `resign` 包而解析失败。
