# 黄色导流线完整版本实现记录

> 日期：2026-07-16  
> 项目：NebulaeCraft（Minecraft 1.12.2 Forge / Java 8）

本文记录从既有白色导流线复制出全套黄色版本，以及后续贴图抗锯齿白边和中英文本地化名称调整的过程。

## 1. 最终需求

- 完整参照既有白色导流线，实现对应的黄色版本。
- ID 在原有 `asphalt_parallel_` 后插入 `yellow_`。
- 方块状态、方块模型、物品模型、贴图、斜线 subtype 和台阶版本全部独立命名。
- 黄色主体色使用 `#efde24`，即 RGB `(239, 222, 36)`。
- Photoshop 旋转产生的灰白抗锯齿边缘也必须转换成对应的黄色过渡色。
- 中文名称：
  - 既有白色版本统一增加“白”，如“白导流线”。
  - 黄色版本统一使用“黄”而不是“黄色”，如“黄导流线”。
- 英文名称：既有白色版本增加 `White`，黄色版本使用 `Yellow`。

## 2. 新增方块 ID

共新增 16 个方块 ID：

| 类型 | ID |
|---|---|
| 完整导流线 | `asphalt_parallel_yellow_full` |
| 完整导流线台阶 | `asphalt_parallel_yellow_full_slab` |
| 半导流线 | `asphalt_parallel_yellow_half` |
| 半导流线台阶 | `asphalt_parallel_yellow_half_slab` |
| 左分叉 | `asphalt_parallel_yellow_fork_left` |
| 左分叉台阶 | `asphalt_parallel_yellow_fork_left_slab` |
| 右分叉 | `asphalt_parallel_yellow_fork_right` |
| 右分叉台阶 | `asphalt_parallel_yellow_fork_right_slab` |
| 左合并 | `asphalt_parallel_yellow_merge_left` |
| 左合并台阶 | `asphalt_parallel_yellow_merge_left_slab` |
| 右合并 | `asphalt_parallel_yellow_merge_right` |
| 右合并台阶 | `asphalt_parallel_yellow_merge_right_slab` |
| 左斜线（两个 subtype） | `asphalt_parallel_yellow_diagonal_left` |
| 左斜线台阶（两个 subtype） | `asphalt_parallel_yellow_diagonal_left_slab` |
| 右斜线（两个 subtype） | `asphalt_parallel_yellow_diagonal_right` |
| 右斜线台阶（两个 subtype） | `asphalt_parallel_yellow_diagonal_right_slab` |

## 3. Java 注册实现

普通直线、半线、分叉、合并及其台阶共 12 个方块批量注册在：

```text
src/main/java/net/kuina/nebulaecraft/util/RegistryHandler.java
```

左右斜线及台阶需要保留既有的 subtype 和朝向逻辑，因此分别复制为四个独立类：

```text
BlockAsphaltParallelYellowDiagonalLeft.java
BlockAsphaltParallelYellowDiagonalLeftSlab.java
BlockAsphaltParallelYellowDiagonalRight.java
BlockAsphaltParallelYellowDiagonalRightSlab.java
```

四个类继续使用 `@ElementsNebulaecraftMod.ModElement.Tag` 自动发现，并完全保留白色版本的以下行为：

- `subtype0` / `subtype1`
- 水平朝向和旋转
- metadata 编解码
- 创造模式中的两个 subtype 物品
- subtype 对应的独立物品模型注册

## 4. 新增资源

新增资源总计 66 个：

| 资源类型 | 数量 |
|---|---:|
| blockstate | 16 |
| 方块模型 | 20 |
| 物品模型 | 20 |
| PNG 贴图 | 10 |

斜线的 `subtype1` 继续使用文件名中的 `_v` 模型和贴图。黄色方块模型的侧面线条引用由白色版本的：

```text
nebulaecraft:blocks/asphalt_white_line
```

改为：

```text
nebulaecraft:blocks/asphalt_yellow_line
```

## 5. 贴图换色

### 5.1 主体颜色

白色导流线主体实际不是 `(255,255,255)`，而是：

```text
(250, 250, 250)
```

黄色主体替换为：

```text
#efde24 = (239, 222, 36)
```

10 张新贴图均为 256×256 RGBA PNG。主体之外的沥青和 Alpha 通道保持不变。

### 5.2 Photoshop 抗锯齿白边修复

第一次只替换纯色主体后，旋转图层产生的灰度过渡像素仍然保留，因而在黄色边缘形成白边。

项目内已有成对的白线/黄线贴图，可用于验证原有混色方式。设：

- `Cw`：白线贴图中的灰度过渡像素
- `B`：该位置对应的沥青底色
- `A`：白线图层在该像素的覆盖率
- `Y`：目标黄色 `(239,222,36)`

先从白色过渡像素反推覆盖率：

```text
A = (Cw - B) / (250 - B)
```

再对目标黄色的三个通道分别与底色混合：

```text
Cy = round(B + A × (Y - B))
```

示例：

```text
(187,187,187) -> (179,167,32)
(139,139,139) -> (133,125,32)
```

底图方向通过原始 `asphalt.png` 的旋转/翻转候选和边缘邻域匹配确定，以兼容经过翻转或局部旋转的导流线贴图。

最终共重新着色 6,140 个抗锯齿过渡像素。验证结果：

- 所有检测到的过渡像素均已变为黄色系，不再残留灰白像素。
- 黄色实体区域仍严格为 `#efde24`。
- Alpha、尺寸和非线条区域保持不变。

## 6. 最终本地化规则

修改文件：

```text
src/main/resources/assets/nebulaecraft/lang/zh_cn.lang
src/main/resources/assets/nebulaecraft/lang/en_us.lang
```

### 中文

| 版本 | 示例 |
|---|---|
| 既有白色版本 | `白导流线`、`白导流线左斜线`、`白半导流线` |
| 新增黄色版本 | `黄导流线`、`黄导流线左斜线`、`黄半导流线` |

黄色名称不使用“黄色”二字，统一缩写为“黄”。

### 英文

| 版本 | 示例 |
|---|---|
| 既有白色版本 | `White Guide Line`、`White Guide Line Left Diagonal`、`Half White Guide Line` |
| 新增黄色版本 | `Yellow Guide Line`、`Yellow Guide Line Left Diagonal`、`Half Yellow Guide Line` |

中英文各包含 20 条白色名称和 20 条黄色名称。

## 7. 验证结果

本次实现执行了以下检查：

- 56 个新增 JSON 文件全部通过语法解析。
- blockstate、方块模型、物品模型和贴图引用关系完整。
- 10 张黄色贴图通过尺寸、Alpha、主体色和过渡像素检查。
- 中英文名称数量和颜色前缀检查通过。
- Java 8 下执行：

```text
./gradlew compileJava processResources
```

结果：

```text
BUILD SUCCESSFUL
```

## 8. 后续注意事项

- 若继续从白色道路标线制作其他颜色版本，不能只替换纯白主体，还应处理旋转或缩放产生的抗锯齿过渡像素。
- 过渡色应按原始覆盖率和底色重新混合，避免使用统一阈值直接覆盖沥青细节。
- 新增 subtype 方块时，应同时检查 blockstate、物品 metadata 模型和本地化键，不能只复制普通 metadata 0 模型。
