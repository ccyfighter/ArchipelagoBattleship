# 群岛海战棋 · Archipelago Battleship

Android 14+ 原生策略战棋（Kotlin + Jetpack Compose Material 3 + Coroutines/StateFlow +
kotlinx.serialization）。单机 AI 对战 / 双机面对面局域网联机，纯本地、无公网服务器、
不依赖 Google Play Services。

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
  src/main/res/drawable/ic_launcher_{background,foreground}.xml
  src/main/res/mipmap-anydpi-v26/ic_launcher{,_round}.xml
  src/main/java/com/archipelago/battleship/
    MainActivity.kt
    core/    AppTheme.kt          双主题配色 + Material3 主题
             ShipGlyph.kt         《战舰世界》舰种矢量标识（Canvas）
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
鱼雷机空袭（起点+航向 3 格直线）CD3/全局2次、高能燃烧弹全局2发+AA瘫痪、潜航鱼雷+潜望镜隐蔽+强制上浮、
区域防空网、紧急规避机动全局1次、深水炸弹炮、主动水听声呐、灵活规避、
呼叫陆航空袭延时1回合且无视一切AA）都在 `domain/engine/GameEngine.kt` 中实现，
编成界面会展示当前选中舰种的完整数值说明。
侦察机 / 水听声呐 / 主炮齐射 / 鱼雷机空袭自第 2 回合起可用，燃烧弹第 3 回合起，陆航空袭第 4 回合起。

### 回合流
回合初自动结算延时空袭 / 刷新 CD / 解除 AA 瘫痪 / 更新潜艇上浮（含【上浮暴露复核】）→
阶段一（可选 1 次战术行动，或显式点「进入阶段二」）→
阶段二（攻击名额 = min(存活舰船数, `Rules.BASE_ATTACKS_PER_TURN`，默认 3)，每舰 1 次，随时可结束回合）
→ 180s 超时按剩余未行动舰船数自动随机常规炮击并结束回合。
所有操作都是「选船 → 选目标（可反复改） → 点确认」，不自动切船、不自动释放技能、不自动跳阶段；
本回合内可「撤销 / 重做」，换手自动清空撤销栈。
除十字 5 格范围伤害外，单点攻击禁止选已攻击过的格子（候选格高亮 + 引擎二次校验）。
棋盘对每格显示结果标记：○ 未命中 / X 命中 / S 击沉 / AA 战机被击落 / - 免疫或阻挡，
金色呼吸框标出最近一次攻击；顶部另有最近 3 条战果彩色横幅。

### 【上浮暴露复核】（潜艇专属，解决"打过的格子锁死"）
水下潜艇首尾两格被常规炮击时按规则反馈「未命中（水花）」，同时坐标会进入去重集合。
若潜艇此后强制上浮，这些格子本应可被击中，却因去重无法再打。处理方式是：
**上浮那一刻自动把此前「因潜望镜隐蔽而报 MISS 的单点炮击」复核为命中**，
直接对潜艇造成 1 格伤害、棋盘上对应 ○ 自动变 X、战况播报 `【上浮暴露】…`，
命中按原攻击者归因（MVP 统计不漏）。玩家不额外获得攻击机会，坐标依旧保持去重，
也不会因为"哪些格子被解锁"而泄露潜艇位置。
只匹配落在潜艇首尾格、且当时「没找到船」的单点 MISS；空海水花、十字范围伤害、
「该舰格已损毁」、侦察 / 深弹 / AA 拦截等记录一律不参与，其他舰船打击逻辑零改动。
复核后记录由 MISS 改为 HIT，因此后续再次上浮不会重复结算。

### 鱼雷机突防（航空巡洋舰）
阶段二技能，第 2 回合起 / CD3 / 全局 2 次：先点突防起点，再点与其同行或同列的一格指定航向
（第二点仅定方向，不要求该格未被攻击过）。沿航向对「起点 + 之后 2 格」共最多 3 格逐格判定，
遇山地或命中第一艘船即停；整条航线任一格落入敌方防空区则战机被击落、空袭全部失效（陆航空袭除外）。
属于范围伤害，不写入坐标去重，也无法伤害水下潜艇。棋盘会以高亮航线预览 3 格范围。

### 沉没舰船
已沉没舰船在所有行动入口（开火 / 技能 / AI / 超时托管）统一被拦截，且会播报
`【已沉没】… 无法再开火或释放技能`；界面同步移除其选船与技能按钮。敌方棋盘上仍会保留
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
6. 主题 B 的雷达扫描是 Canvas 扇形 + 射线动画；主题 A 是米黄泛旧方格纸 +
   钢笔蓝网格 + 红蓝铅笔命中叉/水花圈，都是矢量绘制，不依赖任何图片资源。
