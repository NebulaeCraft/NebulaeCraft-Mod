# autogen 道路中间隔离带

> 日期：2026-11-06  
> 适用：Minecraft Forge 1.12.2 / Java 8

## 存档样板

直接读取 `run/saves/Hello World!/level.dat` 的 Forge 方块 ID 映射，以及
`region/r.-3.-1.mca` 的方块和 metadata。参考区域为
`X=-1341..-1337、Y=2..4、Z=-491..-481`，沿 X 方向延伸，中央为 `Z=-486`。

样板是 11 格宽的双向四车道道路。从北到南的路面横断面如下：

```text
Y=4       A
Y=3       M
Y=2  W.W.YCY.W.W
```

- `W`：白线，两侧为实线，内侧为白虚线。
- `.`：纯沥青车道。
- `Y`：中央两侧的单黄实线，`nebulaecraft:asphalt_yellow_line@2`。
- `C`：`railcraft:reinforced_concrete@8`。
- `M`：`nebulaecraft:median_strips@3`。
- `A`：`nebulaecraft:antiglare_shield@3`。

两个白虚线带在五格样板内均为 `.WWW.`，属于 `3+3` 组合中的一段。
区域内共 16 格白线、24 格纯沥青、10 格黄线、5 格混凝土、5 格隔离带、5 格防眩板。
其余 100 格为空气；模板延续既有道路逻辑，不额外清空整片路面上方空间。

## 使用

按确认的范围，仅双向道路增加“中间隔离带：否／是”，默认“否”。
该循环控件与“道路类型：地面／桥梁”并排，左侧道路类型、右侧中间隔离带，
各宽 127 像素，中间留 6 像素。单向道路保留原道路类型控件。

| 模板 | 无隔离带路宽 | 带隔离带路宽 |
| --- | --- | --- |
| `ground_road_two_way_2` | 5 格 | 7 格 |
| `ground_road_two_way_4` | 9 格 | 11 格 |

两幅道路向外各移动一格，保留每方向车道数量、白边线与车道间白虚线，
原中央黄线改为混凝土及两侧单黄实线。双向双车道开启隔离带后没有虚线，
此时虚线组合参数不影响结果。标记仍位于中央路面层，地面和桥梁均可开启隔离带。

命令在既有可选参数后增加可选 `median`，兼容旧命令；可省略虚线组合或道路类型：

```text
/ntunnel preview ground_road_two_way_4 3+3 ground median
/ntunnel preview ground_road_two_way_4 3+3 bridge median
/ntunnel preview ground_road_two_way_2 bridge median
/ntunnel preview ground_road_two_way_2 median
/ntunnel confirm
```

## 实现

- `GroundRoadProfile.withMedian()` 由原双向道路断面派生隔离带断面，扩宽并替换中央标线。
- 中央混凝土沿原中心路线铺设。其占位参与标线交叉检查，第一层黄线不得因奇数半宽
  的拐角取整移入中央；后续道路带继续沿用逐层扩张和斜线错位修正规则。
- 隔离带及防眩板按中心路线的真实邻接方向使用普通或 `_diagonal` 方块。
  四种转角的朝向与道路斜线一致，直线沿 X 时使用样板的 metadata `3`。
- 坡道沿用连续高程和半格量化。中央混凝土使用已有
  `nebulaecraft:reinforced_concrete_slab@0` 下半台阶；低侧保留完整混凝土并在上方加台阶，
  高侧替换为台阶，与沥青路面的表面高度一致。
- 隔离带和防眩板在台阶上使用各自 `subtype1`（普通／斜线的 metadata 均为原朝向加 `4`），
  放在台阶上方一格和两格的位置，模型下移半格后与混凝土台阶衔接。
- 预览包括扩宽后的路面与防眩板高度；混凝土、路面和设施沿既有施工阶段排序。
  新增方块全部参与高度和数量限制，沿用服务器预览、权限、分 tick 生成、取消和撤销流程。
  参数继续使用既有网络包传输，运行及发布时不读取参考世界，无需注册新方块。

## 编译验证

```powershell
$env:JAVA_HOME = 'D:\App\Java\zulu-8'
.\gradlew.bat --offline '-Dfile.encoding=UTF-8' compileJava processResources
```

结果：`BUILD SUCCESSFUL`，两个任务均执行通过。

另用临时离线几何检查复核两种隔离带断面，共 216 组四向直线、左右浅弯、90° 弯及
上下坡路线，均通过道路带连续、标线互不重叠且不侵入中央混凝土的检查。
