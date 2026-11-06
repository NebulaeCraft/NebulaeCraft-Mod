# autogen 逐步施工动画与放置音效

> 日期：2026-11-06  
> 适用：Minecraft Forge 1.12.2 / Java 8

## 需求与原因

原公共执行层每 tick 最多处理 4096 个方块，短结构在视觉上几乎瞬间完成。
地铁模板原先按断面占位的冲突优先级排序，混凝土、道床、轨道及接触网的实际施工顺序不够清晰。

## 当前行为

- 所有内置结构都使用服务器分 tick 施工；每 tick 最多改变 8 格，正常 20 TPS 下约 160 格/秒。
- 地铁桥梁、隧道先清理计划中的净空，再依次生成混凝土、道床、轨道、逃生平台/护栏/灯、接触网。
- 混凝土包含完整方块、水平半砖和垂直半砖，按世界 Y 从下到上逐层生成，每层从选区起点向终点推进。
- 其他施工阶段按离方块最近的水平路线断面从起点向终点推进，同一断面内先低后高。
- 地面道路按同样的路线顺序生成，同一柱中的低侧路面先于上方过渡半砖。
- 放置音效使用实际方块的 `SoundType.getPlaceSound()`，服务器向同维度附近玩家广播，最多每 2 tick 一次、每次选取当批最后一个实际放置的方块，避免大量音效叠加。
- 空气、无变化操作、轨道定向修正及撤销不播放放置音效。
- 取消仍立即停止施工并分批回滚；撤销保留原来的高吞吐量，每 tick 最多恢复 4096 格。

## 实现

- `AutogenBuildOrder` 只重排最终方块操作，不修改各模板用于解决截面重叠的 `priority`，也不改变最终材料、坐标和 metadata。
- 模板显式声明混凝土、道床、轨道和设施的方块材料，其余地铁材料为最后施工的接触网。
- 水平柱的最近路线断面只计算一次，供该柱内所有高度及轨道修正操作复用。
- Railcraft 的非重复撤销轨道定向复写放在全部轨道放置之后、设施施工之前，保持弯轨与坡轨方向。
- `AutogenConfig.blocksPerTick = 8` 控制可见施工速度；`operationsPerTick = 4096` 限制单 tick 总处理量；`placementSoundIntervalTicks = 2` 控制声音间隔。
- `TunnelGenerationManager` 仅将实际变化且需要记录撤销的操作计入施工预算；原有状态和 TileEntity NBT 的捕获流程保留，包含原本状态相同的格子，以保护后续可能受邻轨影响的已有轨道。
- 方块通过原有 `WorldServer.setBlockState(..., 2)` 同步给客户端，不使用客户端假方块，附近多人可看到同一施工进度。

## 编译验证

使用本机 JDK 8，执行：

```powershell
$env:JAVA_HOME = 'D:\App\Java\zulu-8'
.\gradlew.bat --offline '-Dfile.encoding=UTF-8' --rerun-tasks compileJava processResources
```

显式指定 UTF-8，避免 Windows 默认 GBK 读取现有中文源码时产生字符编码诊断。

结果：`BUILD SUCCESSFUL`，`compileJava` 与 `processResources` 均执行通过。
