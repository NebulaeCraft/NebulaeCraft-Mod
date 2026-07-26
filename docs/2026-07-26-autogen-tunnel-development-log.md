# 自动隧道生成工具开发记录

> 日期：2026-07-26  
> 项目：NebulaeCraft / Minecraft Forge 1.12.2 / Java 8  
> 功能文档：[`autogen-tunnel.md`](autogen-tunnel.md)

## 1. 原始需求

参考 `docs/2026-07-26 15-04-04.mp4`，新增类似 WorldEdit 的隧道自动生成工具：玩家使用工具依次选择两个带方向的标记方块，再通过指令预览和确认生成一条圆滑、曲率缓和的地铁隧道。

确定的注册 ID：

- 标记方块：`nebulaecraft:autogen_marker`
- 选择工具：`nebulaecraft:autogen_wand`

允许使用项目现有 `RegistryHandler` 注册上述方块和物品。

## 2. 路线、交互与指令

- 标记方块使用可旋转箭头表达端点方向；空手右键旋转。
- 魔杖依次右键记录起点与终点，选区按玩家保存；两个箭头必须相向。
- 标记坐标代表中央道床位置，生成时被道床替换，撤销时恢复。
- 路线使用五次 Hermite 曲线，端点保持零曲率及零坡度变化。
- 默认最小半径 12 格、最大坡度 6.25%。
- 默认最长路线 2048 格、最多修改 500,000 个方块、每 tick 修改 4096 个方块。
- 预览有效期 60 秒；全服同一时间只运行一个生成或撤销任务。
- 最近一次生成保存原方块状态和 TileEntity NBT，可在取消、服务器中断或执行撤销时分批恢复。

指令：

```text
/nebulaetunnel preview <preset> <platformColor> <power> [mirror]
/nebulaetunnel confirm
/nebulaetunnel cancel
/nebulaetunnel status
/nebulaetunnel undo
/nebulaetunnel reload
/nebulaetunnel clear
```

别名为 `/ntunnel`，权限等级为 2。供电方式包括：

- `none`
- `catenary`
- `thirdrail_white`
- `thirdrail_yellow`

## 3. 材料约定

- 整块衬砌：Railcraft `railcraft:reinforced_concrete` meta 8（light gray）。
- 水平台阶：`nebulaecraft:reinforced_concrete_slab` subtype1。
- 垂直台阶：`nebulaecraft:reinforced_concrete_vertical_slab`。
- 中央道床：旧 ID `43:8`，即 `minecraft:double_stone_slab` meta 8。
- 两侧道床：旧 ID `44:0`，即 `minecraft:stone_slab` meta 0。
- 强化轨道：`railcraft:track_flex_reinforced`；兼容配置别名 `railcraft:reinforced_track` 和 `railcraft:track_reinforced`。
- 金属平台：`railcraft:post_metal_platform`，颜色由指令决定。
- 接触网模式：使用 NebulaeCraft 钢性接触网系列，而非柔性接触线系列。
- 默认平台位于线路左侧、第三轨位于右侧；`mirror` 同时交换两者。

## 4. 最终直线断面

标记所在中央道床为 Y=0。断面固定为 7×7，从上到下为：

```text
强化混凝土 × 7
强化混凝土 × 2 | subtype1上台阶 | 钢性接触网 | subtype1上台阶 | 强化混凝土 × 2
强化混凝土 | 空气 × 5 | 强化混凝土
垂直强化混凝土台阶 | 空气 × 5 | 垂直强化混凝土台阶
垂直强化混凝土台阶 | 空气 × 5 | 垂直强化混凝土台阶
强化混凝土 | 空气 × 2 | 强化轨道 | 空气 × 2 | 强化混凝土
强化混凝土 × 2 | 44:0 | 43:8 | 44:0 | 强化混凝土 × 2
```

平台、第三轨和灯具是设施覆盖层，会占用相应净空格，但不再替换结构衬砌。金属平台放在完整左侧基础方块上方。

## 5. 旋转方块方向修正

调试时确认 NebulaeCraft 多个带旋转功能方块的子 ID 与原版水平 metadata 规则不同，不能直接假定 `EnumFacing` 的原版序号。

生成器改为显式使用本模组的模型状态映射：

```text
NORTH = 0
SOUTH = 1
WEST  = 2
EAST  = 3
```

并按各模型的实际语义分别处理：

- 垂直台阶和隧道灯：状态表示模型朝向净空，实体贴在相反侧。
- 第三轨直线：`facing` 表示导电轨位于当前方块的哪一侧，不表示线路前进方向。
- 第三轨坡道：结合导电轨所在侧和真实上升方向选择 `slope_1` / `slope_2`。
- 第三轨弯道：根据第三轨处于曲线内侧还是外侧选择 `diagonal_2` / `diagonal_1`，再按实际拐角设置方向。
- 第三轨坡道只替换实际坡轨所在的低端切面，高端相邻平轨恢复普通第三轨。
- 第三轨在有效长度索引 `0, 3, 6...` 使用对应颜色的支架模型；直线使用 `*_support`，弯道使用 `*_support_diagonal`，坡道因没有专用支架模型而保留坡道。
- 单格横移时，第三轨斜线对固定依次使用 `diagonal_1、diagonal_2`。`diagonal_1` 锚定在首个轨道转角的前一索引；`diagonal_2` 使用首个转角索引的模型状态，实际位置同时沿入口反方向和横移方向各移动一格，从而落在 `diagonal_1` 的横向相邻空气格，并位于第一格出段直线第三轨的纵向相邻格。`diagonal_1` 使用首个转角方向的反向，`diagonal_2` 使用第二个转角的原始方向，不能共用同一套 180° 修正。斜线支架仅能替换 `diagonal_1`。
- 支架有效长度计数会把相邻的一组 `diagonal_1 + diagonal_2` 合并为一格；连续 45° 斜线路段拆成首尾相接的单格横移单元，每个单元使用与已验证单格偏移相同的位置和朝向，不再让中间单元退回普通转角逻辑，也不生成额外直线格。
- 钢性接触网坡线：按真实上升方向设置。
- 钢性接触网斜线：按路径前后连接形成的拐角设置。
- Railcraft 强化轨道继续使用其 `EnumRailDirection` metadata。

## 6. 隧道灯规则

最初实现为每 8 格在左右墙轮流设置一盏灯。后续修正为：

- 所有灯具均集中在金属平台一侧。
- `lightSpacing` 直接作为路线索引周期；默认值 9 对应索引 `0, 9, 18...`，两盏灯之间保留 8 格。
- 使用 `mirror` 时，平台和全部灯具同步换边。

## 7. 弯道完整断面过渡

方格化平滑曲线的一格横向换位会形成两个相邻轨道拐角。直接叠加两个普通 7 格断面会导致道床互相覆盖、内部残留墙柱以及洞体收窄。

金属平台不跟随两个相邻弯轨硬连接成连续面。朝平台侧换位时，删除旧侧末端两格并在新侧提前补一格，使两段平台只保持对角相邻；背离平台侧换位时执行纵向镜像规则，`mirror` 后同样成立。

最终实现为旋转适配四个方向的 3×8 完整断面过渡。最底层沿换位方向的三行依次为：

```text
CC | 44 | 43 | 44 | 44 | CC
CC | 44 | 43 | 43 | 44 | CC
CC | 44 | 44 | 43 | 44 | CC
```

其中 `CC` 表示两格强化混凝土。其余各层按同一规律同步调整：

- 顶板三行均扩宽为 8 格强化混凝土。
- 普通净空层变为两侧外墙包围 6 格净空。
- 明确清除两个旧断面重叠后位于内部的墙块。
- 两层垂直台阶移动至过渡区的新外缘。
- 接触网槽按 `1格 → 2格 → 1格` 横向换位。
- subtype1 上台阶按与 `44:0` 相同的规律补齐。
- 两个中央道床上分别保留 Railcraft 强化弯轨。
- 平台、第三轨、接触网和灯具优先级高于结构清空操作，不会被过渡模板误删。
- 接触网支撑间隔命中斜线时不再覆盖斜线模型，而是在前后两格内寻找最近的水平直线格；等距时固定优先前一格，四个候选均不符合时忽略本次支撑放置。
- 隧道灯命中扩宽过渡的内缩半区时向平台侧外移一格，贴合实际外墙。
- 初版曾在三行过渡断面的横向外侧各补两格高背衬，实际会在洞体外形成凸块。最终改为仅在过渡矩形与前后普通断面的两个对角接缝补完整 7 格高强化混凝土立柱，位置对应外墙换位产生的真实缺角，同时保持普通直线断面不变。

## 8. 坡道专用断面

强化轨道位于一格高差的下端并使用上坡轨道状态时，不再沿用普通 7 层断面，而使用 8 层专用断面：

- 顶板相对道床抬高至第 8 层。
- 接触网凹槽的 subtype1 台阶位于坡线接触网上方一层。
- 左右垂直强化混凝土台阶由两层增为三层。
- 坡道轨道下方三格道床全部使用 `43:8`。
- 上端平轨格保持普通断面，使其顶板高度与坡道格抬高后的顶板一致。
- 隧道灯命中坡道轨道格时向上移动一格。
- 接触网支撑间隔命中坡线时，采用与斜线相同的前后两格避让规则；没有水平直线候选则跳过本次支撑。
- 坡线接触网仅放置在实际使用上坡轨道状态的低端切面；相邻高端平轨切面恢复直线接触网。
- `lightSpacing` 与 `catenarySupportSpacing` 直接作为索引周期使用，不在运行时执行 `+1`；两者默认值均为 9，目标索引为 `0, 9, 18...`。

## 9. 崩溃排查

首次进入世界时，`crash-2026-07-26_16.41.04-server.txt` 显示 Railcraft 在反序列化进度条件时读取 `TileEntity.REGISTRY`，触发 `IllegalAccessError`。

最终处理：

- 新增 `META-INF/nebulaecraft_at.cfg`。
- 公开 `net.minecraft.tileentity.TileEntity field_190562_f`（`REGISTRY`）。
- 在 ForgeGradle 配置和 JAR manifest 中声明该 Access Transformer。
- 删除 `RegistryHandler` 中引用客户端专用字段的调试输出，避免独立服务端加载失败。

随后进入包含 Railcraft 箱子矿车的世界时，列车存档读取又因访问 `NBTTagList.tagList` 触发 `IllegalAccessError`（`crash-2026-07-26_17.40.18-server.txt`）。因此最终 AT 文件同步 Railcraft 当前实际触发的两条必要规则：

- `TileEntity.REGISTRY`
- `NBTTagList.tagList`

未把 Railcraft AT 中其余尚未触发的字段规则无差别复制进项目。

补充处理 ForgeGradle 3 开发运行缓存问题：增加 `prepareDevForgeJar`，从不含 `Side.BUKKIT` 的 `-recomp.jar` 生成带两条 MCP 名访问规则的本地开发副本，并在 `runClient`、`runServer` 启动前自动替换 joined 中间包。否则新增第二条 AT 后会在进入主菜单前先于世界加载触发 `NetworkRegistry.newChannel` 空指针，这不是隧道或 Railcraft 存档逻辑本身的异常。

## 10. 配置迁移

配置文件：

```text
config/nebulaecraft/tunnel_presets.json
```

当前 `schemaVersion` 为 5。加载旧配置时自动执行：

- `clearWidth: 6`、`clearHeight: 5` 迁移为当前固定参考断面参数。
- 水平强化混凝土台阶由 subtype0 迁移为 subtype1。
- 旧默认设施周期 `lightSpacing: 8`、`catenarySupportSpacing: 8` 迁移为直接索引周期 9。
- 增加默认第三轨支架索引周期 `thirdRailSupportSpacing: 3`。
- 默认预设 ID 仍保留为 `metro_6x5`，避免破坏已有指令和配置。

## 11. 主要实现文件

- `src/main/java/net/kuina/nebulaecraft/autogen/TunnelBuilder.java`
- `src/main/java/net/kuina/nebulaecraft/autogen/TunnelConfig.java`
- `src/main/java/net/kuina/nebulaecraft/autogen/CommandNebulaeTunnel.java`
- `src/main/java/net/kuina/nebulaecraft/autogen/AutogenSelection.java`
- `src/main/java/net/kuina/nebulaecraft/block/BlockAutogenMarker.java`
- `src/main/java/net/kuina/nebulaecraft/item/ItemAutogenWand.java`
- `src/main/resources/META-INF/nebulaecraft_at.cfg`
- `gradle/nebulaecraft_dev_at.cfg`

## 12. 验证与测试流程

每次主要修改后均使用 JDK 8 执行编译；最终完整弯道断面修改通过：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home \
  ./gradlew -Dnet.minecraftforge.gradle.test_certs=false compileJava processResources

env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home \
  ./gradlew -Dnet.minecraftforge.gradle.test_certs=false jar
```

构建结果：

```text
build/libs/NebulaeCraft-1.12.2-2.25.jar
```

第二次世界加载崩溃修复后还验证了：

- `compileJava processResources prepareDevForgeJar` 成功。
- 开发 Forge 副本中仅有 `Side.CLIENT`、`Side.SERVER`，且 `TileEntity.REGISTRY`、`NBTTagList.tagList` 均为 public。
- `runClient` 使用该副本启动，Forge、Railcraft、NebulaeCraft 均完成加载并进入主菜单，不再出现 `NetworkRegistry` 初始化崩溃。

游戏内重复测试前，应先执行：

```text
/ntunnel undo
/ntunnel reload
```

然后重新选择标记、执行 `preview` 并确认生成，避免旧结构影响观察结果。
