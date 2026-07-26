# 参考截面自动隧道生成工具

> 日期：2026-07-26  
> 适用：NebulaeCraft / Minecraft Forge 1.12.2 / Java 8

## 物品与标记

- `nebulaecraft:autogen_marker`：跨栏式方向标记，橙色箭头表示方向。放置后可空手右键旋转。
- `nebulaecraft:autogen_wand`：依次右键起点和终点标记以记录选区；潜行右键空气可清除选区。
- 两个箭头必须相向。标记所在方块会成为中央道床方块，生成完成后由道床替换，执行撤销时恢复。

## 指令

完整指令名为 `/nebulaetunnel`，可缩写为 `/ntunnel`，需要权限等级 2。

```text
/ntunnel preview <preset> <platformColor> <power> [mirror]
/ntunnel confirm
/ntunnel cancel
/ntunnel status
/ntunnel undo
/ntunnel reload
/ntunnel clear
```

默认预设仍命名为 `metro_6x5`，以兼容已有指令和配置。供电参数：

- `none`
- `catenary`
- `thirdrail_white`
- `thirdrail_yellow`

示例：

```text
/ntunnel preview metro_6x5 cyan catenary
/ntunnel preview metro_6x5 yellow thirdrail_yellow mirror
/ntunnel confirm
```

平台颜色支持 Railcraft 的 16 色英文名，额外接受 `silver`、`lightGray`、`lightBlue` 别名。

## 默认断面与材料

- 断面固定为 7×7；从上至下逐层为：
  - `7×强化混凝土`
  - `2×强化混凝土 + 强化混凝土上台阶(subtype1) + 钢性接触网 + 强化混凝土上台阶(subtype1) + 2×强化混凝土`
  - `强化混凝土 + 5×空气 + 强化混凝土`
  - `强化混凝土垂直台阶 + 5×空气 + 强化混凝土垂直台阶`（连续两层）
  - `强化混凝土 + 2×空气 + 强化轨道 + 2×空气 + 强化混凝土`
  - `2×强化混凝土 + 44:0 + 43:8 + 44:0 + 2×强化混凝土`
- 弯道相邻截面的空气区域取并集，避免衬砌在曲线内部形成横向立柱；平台、第三轨和隧道灯作为可选设施占用净空格，但不再替换结构衬砌。
- 方格化曲线发生一格横向换位时，完整七层都使用旋转后的 3×8 专用过渡模板：`43:8` 中央道床和钢性接触网槽按 `1格 → 2格 → 1格` 斜向换位，`44:0` 与 subtype1 台阶分别在两侧补齐；顶板扩宽为 8 格，普通净空层扩为两侧外墙包住 6 格净空，两层垂直台阶同步移动到过渡外缘，避免相邻普通断面互相覆盖形成收窄、缺角和内部墙柱。
- 三行弯道过渡矩形与前后普通断面的两个对角接缝，各补一根从底板到顶板、完整 7 格高的强化混凝土立柱，封闭垂直台阶在接缝处暴露的半方块缺口；不会在断面横向外侧生成凸块，也不改变普通直线断面。
- 整块衬砌：`railcraft:reinforced_concrete` meta 8（light gray）。
- 接触网槽两侧固定使用 NebulaeCraft 水平强化混凝土台阶 `subtype1`；中部两层侧墙固定使用朝外贴墙的垂直强化混凝土台阶。
- 强化轨道处于一格高差的下端、使用坡道模型时，该格改用 7×8 坡道专用断面；从上至下为：
  - `7×强化混凝土`
  - `2×强化混凝土 + 强化混凝土上台阶(subtype1) + 空气 + 强化混凝土上台阶(subtype1) + 2×强化混凝土`
  - `强化混凝土 + 2×空气 + 钢性接触网斜坡 + 2×空气 + 强化混凝土`
  - `强化混凝土垂直台阶 + 5×空气 + 强化混凝土垂直台阶`（连续三层）
  - `强化混凝土 + 2×空气 + 强化轨道斜坡 + 2×空气 + 强化混凝土`
  - `2×强化混凝土 + 3×43:8 + 2×强化混凝土`
  上端的平轨格仍使用普通断面，并与坡道格抬高一层的顶板自然衔接。
- 本模组旋转方块不使用原版水平 metadata 顺序。生成器按实际模型显式使用 `N=0、S=1、W=2、E=3`，并分别处理侧贴模型、第三轨内外弯模型及钢性接触网的斜线/坡线方向。
- 中央道床：旧 ID `43:8`，即 `minecraft:double_stone_slab` meta 8。
- 左右道床：旧 ID `44:0`，即 `minecraft:stone_slab` meta 0。
- 轨道：`railcraft:track_flex_reinforced`；配置读取同时兼容 `railcraft:reinforced_track` 和 `railcraft:track_reinforced`。
- 平台默认位于起点至终点方向的左侧，第三轨位于右侧；`mirror` 会交换两侧。方格化曲线发生一格横向换位时，两段直线平台在转角处保持对角分离：删除旧侧末端两格并把新侧提前一格，不用平台方块硬连接转角；反向换位按纵向镜像处理。
- 第三轨坡道模型仅放置在真正使用上坡轨道状态的低端切面；相邻高端平轨切面恢复普通第三轨。
- 第三轨支架直接以 `thirdRailSupportSpacing` 作为有效线路长度索引周期；默认值为 3，在有效索引 `0, 3, 6...` 使用对应颜色的直线或斜线支架模型，因此两个支架之间保留 2 个长度单位。坡道没有专用支架模型，命中支架索引时保留坡道模型。
- 单格横向换位的第三轨按模型实际跨格方向重新锚定：`diagonal_1` 放在首个轨道转角的前一索引；`diagonal_2` 使用首个转角索引的模型状态，实际位置同时沿入口反方向和横移方向各移动一格，因此位于 `diagonal_1` 的横向相邻空气格，同时也是第一格出段直线第三轨的纵向相邻格，不会覆盖任一方块。`diagonal_1` 使用首个转角模型方向的反向，`diagonal_2` 使用第二个转角的原始模型方向。只有 `diagonal_1` 允许替换为斜线支架。
- 第三轨支架间距使用有效线路长度计数：相邻的一组 `diagonal_1 + diagonal_2` 合计只计一格。连续 45° 斜线会被拆成首尾相接的单格横移单元，每个单元复用已经校准的位置与模型方向，组成连续斜线对；中间不退回普通转角摆放，也不插入额外直线第三轨。
- 隧道灯直接以 `lightSpacing` 作为路线索引周期；默认值为 9，放置索引固定为 `0, 9, 18...`，因此两盏灯之间有 8 格。灯具全部位于金属平台一侧；使用 `mirror` 时与平台同步换边。
- 灯具落在一格横移的斜线过渡区时，会按过渡外墙的实际位置向外移动一格，保持贴墙。
- 灯具落在坡道轨道格时向上移动一格，以贴合坡道专用断面抬高后的完整侧墙。
- 接触网支撑件同样直接以 `catenarySupportSpacing` 作为路线索引周期；默认值为 9，目标索引为 `0, 9, 18...`。若目标格是斜线或坡线接触网，则保留该特化模型，并在前后两格内选择最近的直线接触网格放置支撑件；若四个邻近候选均非直线，本次支撑件放置直接跳过。
- 坡线接触网只生成在真正使用上坡轨道状态的低端切面；与其相邻的高端平轨切面使用普通直线接触网。
- 旧版配置中的 `clearWidth: 6`、`clearHeight: 5` 以及水平台阶 `subtype0` 会自动迁移为新参考截面；旧的默认设施周期 8 会迁移为直接索引周期 9，并补充默认第三轨支架周期 3，同时写入 `schemaVersion: 5`。

## 配置与安全

首次启动后生成：

```text
config/nebulaecraft/tunnel_presets.json
```

默认限制：路线长度 2048 格、修改 500,000 个方块、每 tick 处理 4096 个方块。预览有效期为 60 秒。修改配置后执行 `/ntunnel reload`。

生成前只显示粒子预览和统计，不修改世界。系统按 tick 分批生成并在世界存档中保存最近一次撤销数据，包括 TileEntity NBT。生成途中执行 `cancel` 会自动回滚已修改部分。

## Railcraft 依赖

Railcraft 是必需依赖。隧道代码通过 Forge 注册表查找 Railcraft 方块，不直接引用 Railcraft Java 类。相邻 Railcraft 源码当前注册的强化轨道 ID 是 `railcraft:track_flex_reinforced`。

开发环境通过 `META-INF/nebulaecraft_at.cfg` 同步公开 Railcraft 所需的 `TileEntity.REGISTRY` 和 `NBTTagList.tagList`。这是因为 ForgeGradle 3 的反混淆运行环境会读取 `run/mods` 中外部 Railcraft JAR 的 Access Transformer，却不会把这些 SRG 字段规则提前应用到开发用 Minecraft 依赖；发布 JAR 清单也包含同一 `FMLAT` 声明。

本项目使用的 ForgeGradle 3 旧版 1.12 管线在 AT 内容变化后可能把仍含 `Side.BUKKIT` 的 joined 中间包放进 `runClient`/`runServer` 类路径，导致 `NetworkRegistry.newChannel` 在模组构造阶段空指针。`prepareDevForgeJar` 会从正确的 `-recomp.jar` 生成项目本地副本，使用 `gradle/nebulaecraft_dev_at.cfg` 中的 MCP 字段名应用同样两条规则；两个运行任务会自动使用该副本。此逻辑仅服务开发运行，不进入发布模组。
