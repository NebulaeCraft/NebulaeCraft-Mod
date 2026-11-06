# autogen 道路的地面／桥梁切换

> 日期：2026-11-06  
> 适用：Minecraft Forge 1.12.2 / Java 8

## 存档样板

直接读取 `run/saves/Hello World!/level.dat` 的 Forge 方块 ID 映射、
`data/nebulaecraft_tunnel_undo.dat` 的最新生成范围，以及
`region/r.-3.-2.mca` 的方块和 metadata。

最新道路为双向四车道，路面位于 `X=-1347..-1309、Y=3、Z=-592..-581`，
共 366 格。手动补充的桥梁结构为：

- 每个路面格下方一格放置 `railcraft:reinforced_concrete@8`，共 366 格。
- 两侧在路面外扩一格，`Y=3、4` 均放置相同混凝土，每层 84 格。
- 两侧 `Y=5` 放置 `nebulaecraft:road_fence@0`，共 84 格。
- 桥梁两端开放，不生成端部围栏；样板不包含桥墩。

平直处的横断面如下，`C` 为强化混凝土，`F` 为道路栏杆，`R` 为原道路：

```text
Y=5  F         F
Y=4  C         C
Y=3  CRRRRRRRRRC
Y=2   CCCCCCCCC
```

弯道外围先按四向邻格扩张，再补齐外侧肘点。存档中单纯扩张得到 78 格，
补齐拐角后为 84 格，与手动样板完全吻合；两层混凝土和栏杆共用此轮廓。

## 使用

所有四个道路模板均增加循环点击的“道路类型：地面／桥梁”，默认“地面”。
有虚线的模板将其显示在“虚线实空组合（格）”下方；单向单车道只显示道路类型。
现有 GUI 按服务器提供的参数顺序创建循环切换按钮，无需增加专用客户端控件或网络包。

保留原模板 ID 和旧命令用法。道路类型为可选末尾参数；有虚线的模板还可省略
虚线参数，仅提供道路类型，此时使用默认 `2+2`：

```text
/ntunnel preview ground_road_two_way_4
/ntunnel preview ground_road_two_way_4 3+3 ground
/ntunnel preview ground_road_two_way_4 3+3 bridge
/ntunnel preview ground_road_two_way_2 bridge
/ntunnel preview ground_road_one_way_2 2+2 bridge
/ntunnel preview ground_road_one_way_1 bridge
/ntunnel confirm
```

## 实现

- `GroundRoadTemplate` 共用道路类型参数、默认值、命令解析与补全。
- `GroundRoadBuilder` 复用原道路路面、标线、弯道和半砖坡道，在桥梁模式下增加
  下方混凝土及两侧混凝土和栏杆。结构高程跟随原连续高程曲线对应的路面基础层，
  标记仍以路面中央方块为基准。
- 桥梁材料仅在桥梁模式下解析；地面道路继续只依赖已有道路方块。
- 桥梁预览向两侧各扩一格，并包括下梁和栏杆的高度；生成计划类型为 `BRIDGE`。
- 公共施工阶段将路面排在设施之前，桥梁按混凝土、路面、栏杆顺序逐步生成。
- 所有新增方块都纳入修改数量和世界高度检查，沿用服务器权限、预览、分 tick
  施工、取消与撤销流程，通过原有世界方块同步支持多人游戏。运行时不读取参考存档。
- 增加中英文道路类型标签和选项译文，无需注册新方块。

## 编译验证

```powershell
$env:JAVA_HOME = 'D:\App\Java\zulu-8'
.\gradlew.bat --offline '-Dfile.encoding=UTF-8' compileJava processResources
```

结果：`BUILD SUCCESSFUL`，两个任务均执行通过。
