# autogen 双向六车道道路模板

> 日期：2026-11-07  
> 适用：Minecraft Forge 1.12.2 / Java 8

## 存档样板

直接读取 `run/saves/Hello World!/level.dat` 的 Forge 方块 ID 映射及
`region/r.-3.-1.mca` 的方块和 metadata，未修改存档。

- 基础模板：`(-1331,1,-423)..(-1319,1,-418)`，宽 13 格、长 6 格，沿 Z 方向延伸。
- 隔离带模板：`(-1316,1,-418)..(-1302,3,-418)`，宽 15 格、高 3 格；
  另核对 `Z=-423..-418`，同一断面按相同的六格周期重复。

基础模板沿 X 从西到东读取，六行依次为：

```text
Z=-423  W.....D.....W
Z=-422  W.....D.....W
Z=-421  W.....D.....W
Z=-420  W.W.W.D.W.W.W
Z=-419  W.W.W.D.W.W.W
Z=-418  W.W.W.D.W.W.W
```

基础区域共 48 格纯沥青、24 格白线和 6 格双黄线。四条车道分隔白虚线均为
3 格实线、3 格空格，外侧白线和中央双黄线为实线。

隔离带模板在 `Z=-418` 的横断面为：

```text
Y=3         A
Y=2         M
Y=1  W.W.W.YCY.W.W.W
```

材料均为已有方块：

| 符号 | 方块及存档 metadata |
| --- | --- |
| `.` | `nebulaecraft:asphalt@0` |
| `W` | `nebulaecraft:asphalt_white_line@1` |
| `D` | `nebulaecraft:asphalt_double_yellow_line@1` |
| `Y` | `nebulaecraft:asphalt_yellow_line@1` |
| `C` | `railcraft:reinforced_concrete@8` |
| `M` | `nebulaecraft:median_strips@1` |
| `A` | `nebulaecraft:antiglare_shield@1` |

标线和设施的 metadata 随生成路线方向调整。隔离带区域 `Z=-423..-418` 共
48 格纯沥青、24 格白线、12 格黄线、6 格混凝土、6 格隔离带和 6 格防眩板。

## 使用

魔杖的地面模板分类新增 `ground_road_two_way_6`，显示名为“双向六车道道路”。
仅提供并排的“道路类型：地面／桥梁”和“中间隔离带：否／是”，默认地面、无隔离带。
虚线固定为 `3+3`，不显示虚线实空组合控件，也不接受或补全 `2+2` / `3+3` 命令参数。
原有道路模板的参数及默认值保持不变。

```text
/ntunnel preview ground_road_two_way_6
/ntunnel preview ground_road_two_way_6 median
/ntunnel preview ground_road_two_way_6 bridge
/ntunnel preview ground_road_two_way_6 bridge median
/ntunnel confirm
```

无隔离带路宽 13 格，开启隔离带后为 15 格；桥梁两侧各增加一格外侧结构。
标记仍位于路面中央。虚线起点相位沿用现有 `3+3` 逻辑 `.WWW..`，
相邻两个斜线方块合计一个逻辑格。

## 实现

- `TwoWaySixLaneRoadTemplate` 使用 `GroundRoadProfile.twoWay(3)` 对应的配置，
  注册在双向四车道模板之后，保留现有模板顺序。
- `GroundRoadTemplate` 增加固定虚线长度的构造入口，统一控制界面参数、命令用法、
  补全和实际生成时的长度，地面、桥梁及隔离带变体均固定使用 `3+3`。
- 复用原有道路弯道、连续高程半砖、桥梁结构、隔离带及防眩板普通／斜线／台阶变体。
  预览、服务器权限、数量及高度限制、逐步施工、取消、撤销和多人同步沿用公共执行层。
  无需注册新方块或网络包，运行及发布时不读取参考世界。

## 编译验证

```powershell
$env:JAVA_HOME = 'D:\App\Java\zulu-8'
.\gradlew.bat --offline '-Dfile.encoding=UTF-8' compileJava processResources
```

结果：`BUILD SUCCESSFUL`，`compileJava` 执行通过，`processResources` 为最新状态。
