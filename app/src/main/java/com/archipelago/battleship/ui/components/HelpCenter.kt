package com.archipelago.battleship.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archipelago.battleship.core.AppThemeMode
import com.archipelago.battleship.domain.engine.Rules
import com.archipelago.battleship.domain.model.FleetCatalog

/** 二级界面「规则手册」的分类页。 */
enum class HelpTopic(val label: String) {
    RULES("玩法"),
    FLEET("舰队"),
    SKILL("技能"),
    LEGEND("图例"),
    ONLINE("联机"),
    UI("界面")
}

/** 顶部 / 底部常驻的圆形问号按钮，点开规则手册。 */
@Composable
fun HelpButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "?",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 各页面统一顶部条：一行标题 + 极短状态 + 问号。
 * 取代原先「大标题 + 大段说明」的排版，保证首屏就是可操作内容。
 */
@Composable
fun ScreenTopBar(
    title: String,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameLogo(modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    fontSize = 10.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
        actions()
        Spacer(Modifier.width(6.dp))
        HelpButton(onClick = onHelp)
    }
}

private data class HelpBlock(val title: String, val lines: List<String>)

@Composable
private fun HelpBlockView(block: HelpBlock) {
    SurfaceCard {
        Text(
            block.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        block.lines.forEach { InfoLine(it) }
    }
    Spacer(Modifier.height(8.dp))
}

private fun rulesBlocks(): List<HelpBlock> = listOf(
    HelpBlock(
        "编成与胜负",
        listOf(
            "每方 5 艘舰船，占 5 + 4 + 3 + 2 + 1 格；先击沉对方全部舰船者获胜。",
            "双方轮流行动，回合有倒计时。点「投降」并二次确认后立即结束本局，判对方获胜。",
            "系统返回键在大厅、战斗中都会先弹确认，不会误退。"
        )
    ),
    HelpBlock(
        "回合流程",
        listOf(
            "每回合分两段：阶段一可释放 1 次战术技能（也可直接跳过）；阶段二为攻击段。",
            "阶段二每回合最多 ${Rules.BASE_ATTACKS_PER_TURN} 次攻击，且不超过存活舰船数；每艘舰每回合最多行动 1 次。",
            "选一艘舰 → 点敌方海域格子 → 点「确认开火」。确认前可反复改选目标，也可用「撤销 / 重做」回退。"
        )
    ),
    HelpBlock(
        "地形与坐标",
        listOf(
            "棋盘横向 A…、纵向 1…；深色标尺带是坐标，选中目标时行列标签同步高亮。",
            "山地不可部署、不可攻击，会阻挡鱼雷、雷达波与水听声波；山地周边 1 圈浅滩禁停 3~5 格大舰与潜艇。",
            "山地总面积不超过全图 20%，每局随机生成。"
        )
    ),
    HelpBlock(
        "战报情报",
        listOf(
            "公开战报只显示「哪一方 + 攻击坐标 + 结果」，不报攻击方式与舰种；只有击沉才公开舰种。",
            "你看不到敌方编成、存活舰种与坐标；对方也看不到你侦察过哪里。",
            "战术诊断（例如战机为何被击落）只发给你自己，不会广播给对手。"
        )
    )
)

private fun fleetBlocks(): List<HelpBlock> {
    val out = ArrayList<HelpBlock>()
    out.add(
        HelpBlock(
            "编成规则",
            listOf(
                "5 / 4 / 3 / 2 / 1 格各一艘，共 15 格。5、4、2 格档二选一，3 格档三选一，1 格档固定高速炮艇。",
                "切换某档舰种时该档旧船会被移除，需要重新放置；放置后停留在当前槽位，不会自动跳到下一艘。"
            )
        )
    )
    for (size in listOf(5, 4, 3, 2, 1)) {
        FleetCatalog.optionsFor(size).forEach { t ->
            val s = FleetCatalog.spec(t)
            out.add(
                HelpBlock(
                    "${s.label} · ${s.size} 格",
                    listOf("进攻：${s.attack}", "技能：${s.skill}", "被动：${s.passive}")
                )
            )
        }
    }
    return out
}

private fun skillBlocks(): List<HelpBlock> = listOf(
    HelpBlock(
        "航空攻击与防空",
        listOf(
            "侦察机：以部署点为中心的十字 5 格计数舰船信号；只有「部署点」落进敌方防空区才会被击落，扫描范围大小与击落判定无关。水下潜艇按潜望镜中心格计。",
            "鱼雷机：第 1 点点投雷起点（须在敌方防空区外），第 2 点点同行 / 同列一格只定航向；鱼雷从起点向前推进 ${Rules.TORPEDO_BOMBER_STEPS} 格，在「碰到敌舰 / 撞山前一格 / 射程尽头」单点爆炸，沿途不留轨迹、中间格后续仍可再打。",
            "区域防空网：以航母本体 5 格 / 防空巡洋舰每格外扩 1 圈构成防空区，只击落「部署点」落在该区的敌方侦察机 / 鱼雷机。飞机飞过该区去炸别处、鱼雷航线或炸点穿过该区都不算被击落。",
            "航母战术轰炸机从己方航母起飞，只有炸弹落在防空舰本体格时才会被近防火力击落；高能燃烧弹命中航母 / 防空巡洋舰可使其 AA 瘫痪 1 个敌方回合。",
            "陆航空袭锁定后延时 1 回合，对十字 5 格执行无视一切 AA 的饱和轰炸。",
            "拦截 / 阻挡只留显示标记，不写坐标去重，换艘舰仍可打同一格。"
        )
    ),
    HelpBlock(
        "潜艇与鱼雷",
        listOf(
            "潜航时仅中心第 2 格可被常规炮击 / 侦察命中，首尾格反馈「未命中」；上浮时三格都能被打，但不论潜航 / 上浮，任何命中中心格的攻击都直接判整艇沉没。",
            "深水炸弹命中水下潜艇首 / 尾格即报「击中」并损毁该格；中心格按上一条直接击沉。",
            "潜航鱼雷：点同行 / 同列一格只定航向，沿直线最多推进 ${Rules.TORPEDO_MAX_STEPS} 格，单点爆炸、不画航线。路径上被炮击过的格子不再挡路，鱼雷照常穿过；只有碰到舰船 / 沉船残骸、撞山前一格、本次射程尽头才会爆炸。",
            "同一方向连射按 4 → 3 → 2 → 1 递减射程；余程归零后该方向只能「空射」（占 1 次攻击、无伤害、仍强制上浮），换方向不受影响。",
            "每次发射后下 1 回合强制上浮；连续潜航 3 回合后第 4 回合强制上浮。",
            "【最后抵抗】只剩潜艇时，开火卡片出现「上浮炮击」开关：改为普通单格炮击（无视 AA、不减射程）、占 1 次攻击名额；立即上浮，首尾格暴露到自己的下回合后自动下潜。"
        )
    ),
    HelpBlock(
        "炮击与情报技能",
        listOf(
            "主炮齐射：同时轰击 2 个不同坐标，两发间距 ≤ 4 格。",
            "主动水听声呐：以投放点为中心扫描 3×3 + 四边中间各外突 1 格（共 13 格），只探潜艇、无视水下隐蔽，山地阻挡声波；只返回发现数量。",
            "监视雷达：以本舰船体为源点向八方向发射射线，遇山即止；10×10 / 12×12 射程 ${Rules.radarRange(12)} 格，14×14 射程 ${Rules.radarRange(14)} 格。只返回覆盖区内水面舰艇 / 已上浮潜艇数量，敌舰有一格入圈即计入，不报坐标与舰种。",
            "紧急规避机动可平移 / 转向至 4 格航程内未被攻击过的合法水域，保留已受损伤；高速炮艇可部署于近岸浅滩，v13 起航空攻击可正常命中它，详见「舰队」。"
        )
    )
)

private fun legendBlocks(): List<HelpBlock> = listOf(
    HelpBlock(
        "四种情报层",
        listOf(
            "侦察机 = 琥珀放大镜；水听 = 蓝色同心波纹；监视雷达 = 青色虚线框；陆航空袭预告 = 橙红斜叉。",
            "四种情报层都只是显示标记，不封锁坐标、不影响任何攻击判定；对手看不到你留下的覆盖层。"
        )
    ),
    HelpBlock(
        "判定标记",
        listOf(
            "灰斜纹 = 已打过；内缩描边框 = 可攻击；蓝空心圆环 = 未命中；红圆白叉 = 命中；深红整船残骸框 = 击沉。",
            "紫菱形盾 = 拦截 / 阻挡；橙色双框 = 最近一击。所有判定格都垫了浅色底衬 + 深色描边，与水面 / 网格明确分开。"
        )
    )
)

private fun onlineBlocks(): List<HelpBlock> = listOf(
    HelpBlock(
        "开局方式",
        listOf(
            "单机：直接开一局本地 AI 对战。",
            "房主：点「创建房间」，配置地图与群岛后显示二维码；客机扫码加入，也可用「附近房间」自动发现或手动输入 IP:端口:密钥。",
            "手动地址格式：192.168.43.1:41234:882731，也可直接粘贴房主二维码里的整串文本。",
            "双方各自选船布阵并点「准备完毕」，房主点「开始战斗」。"
        )
    ),
    HelpBlock(
        "房间规则",
        listOf(
            "房主退出即解散房间，未结束的对局不保存；客机掉线时房间保持开放，重新扫码可回同一房间。",
            "房主可改地图大小与群岛开关，改动会实时同步并重置双方准备与布阵。",
            "战斗中禁言；一局结束后双方可返回房间保持连接、解锁聊天，直接开下一局。",
            "联机两台手机需装同一版本 APK。"
        )
    ),
    HelpBlock(
        "连不上时",
        listOf(
            "确认两台手机装同一版本、处于同一网络，且房主房间仍开着。",
            "客机停在同步页可点「重新向房主同步战况」；必要时让房主重开房间后重新扫码。"
        )
    )
)

private fun uiBlocks(): List<HelpBlock> = listOf(
    HelpBlock(
        "主题与封面",
        listOf(
            "三套主题：坐标纸 / 情报室 / 声呐雷达，可在首页或大厅切换，作用于界面、底纹、棋盘与首页封面。",
            "首页封面下方可切换底图：雷达图（同心环 + 扫描线）或棋盘（坐标格 + 判定标记）。"
        )
    ),
    HelpBlock(
        "战斗界面",
        listOf(
            "「情报视图」默认开启：不画满屏可打击方格，画面只留地形 / 战果 / 情报层；点任意格子弹出该格战况卡。选船 → 点格 → 确认开火照常可用，无需切模式。",
            "关掉情报视图即显示可打击格（内缩描边框 + 角点）。",
            "「敌方海域 / 我方海域」切换看两边棋盘；完整战报用整屏面板一次看完，不用在页面与列表间来回滑动。",
            "棋盘图例默认收起，点「棋盘图例」展开；点「?」可随时回到本手册。",
            "战斗结束后在结算弹窗点「战场复盘」，可切换房主 / 客机的「部署图」与「打击图」，回看双方完整布阵和全部落点。"
        )
    )
)

private fun blocksFor(topic: HelpTopic): List<HelpBlock> = when (topic) {
    HelpTopic.RULES -> rulesBlocks()
    HelpTopic.FLEET -> fleetBlocks()
    HelpTopic.SKILL -> skillBlocks()
    HelpTopic.LEGEND -> legendBlocks()
    HelpTopic.ONLINE -> onlineBlocks()
    HelpTopic.UI -> uiBlocks()
}

/** 规则手册：整屏二级界面，按分类切换，正文中不再出现任何规则说明。 */
@Composable
fun HelpSheet(
    startTopic: HelpTopic,
    mode: AppThemeMode,
    onDismiss: () -> Unit
) {
    var topic by remember { mutableStateOf(startTopic) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "规则手册",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("关闭", fontSize = 12.sp) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                HelpTopic.values().forEach { t ->
                    ChipButton(t.label, topic == t, true) { topic = t }
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                blocksFor(topic).forEach { HelpBlockView(it) }
                if (topic == HelpTopic.LEGEND) {
                    BoardLegend(mode)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
