# RoadmarkText 显示与碰撞框调整记录

> 日期：2026-06-21
> 涉及文件：
> - `src/main/java/net/kuina/nebulaecraft/client/render/RenderRoadmarkText.java`
> - `src/main/java/net/kuina/nebulaecraft/block/BlockRoadmarkText.java`

本文档记录本次对 `RoadmarkText`（道路文字标识）的一系列调整。

---

## 1. 文字居中（修复偏上）

**问题**：文字在游戏中显示偏上。

**原因**：贴图生成时使用 `FontMetrics.getStringBounds()`（逻辑边界），它包含字体的上伸/下伸空白区。对于没有下伸部的字符（数字、大写字母如 `P`、`20`），实际笔画只占上半部分，贴图底部约 20% 是空白，缩放铺满后字被挤到上方。

**修复**：改用文字的 **实际像素轮廓边界（visual bounds）** 来做垂直定位与缩放，使笔画本身正好铺满贴图，从而真正居中；水平方向用 **advance/逻辑宽度**（见第 4 节，保证空格占宽）。

---

## 2. 准心黑色框贴合实际大小

只修改 `getBoundingBox`（准心悬浮时的黑色高亮框），`getCollisionBoundingBox` 保持返回 `NULL_AABB`，因此**玩家始终可以穿过**。

框尺寸根据渲染贴图实际尺寸计算，并以方块中心 `0.5` 对称展开；朝向为 EAST/WEST 时宽长互换（旋转 90°）。

---

## 3. 宽度随字符数变化

- 字符数 **= 1**：使用窄宽度
- 字符数 **≥ 2**（**空格也计入字符数**）：使用宽宽度
- 长度（沿道路方向）恒为 **30px**

约定：一个方块边长 = 16px，渲染四边形使用半宽 `w`（= 像素宽 / 16 / 2）。

### 宽度迭代历史（最终值见下表最后一行）

| 阶段 | 单字符宽 (px) | ≥2 字符宽 (px) | 单字符半宽 w | ≥2 半宽 w |
|------|--------------|----------------|--------------|-----------|
| 初版 | 14           | 24             | 0.4375       | 0.75      |
| 调整 | 16           | 26             | 0.5          | 0.8125    |
| 调整 | 20           | 28             | 0.625        | 0.875     |
| 最终 | **20**       | **28**         | **0.625**    | **0.875** |

长度固定：30px → 半长 `h = 0.9375`。

### 当前实现

渲染（`RenderRoadmarkText.render`）：

```java
double w = (currentText.length() >= 2) ? 0.875 : 0.625; // 半宽：28/16 或 20/16
double h = 0.9375; // 30/16 的一半，整体长 30px
```

准心框（`BlockRoadmarkText.BlockCustom.getBoundingBox`）：

```java
TileEntity te = source.getTileEntity(pos);
int len = 0;
if (te instanceof TileEntityRoadmarkText) {
    String t = ((TileEntityRoadmarkText) te).getText();
    if (t != null) len = t.length();
}
double hw = (len >= 2) ? 0.875 : 0.625; // 半宽：28/16 或 20/16
switch (state.getValue(BlockHorizontal.FACING)) {
    case SOUTH:
    case NORTH:
        return new AxisAlignedBB(0.5 - hw, 0, -0.4375, 0.5 + hw, 0.05, 1.4375);
    case EAST:
    case WEST:
    default:
        return new AxisAlignedBB(-0.4375, 0, 0.5 - hw, 1.4375, 0.05, 0.5 + hw);
}
```

---

## 4. 空格正常显示

**需求**：空格必须计为一个字符且不能不显示。

**实现**：
- 字符数判断使用 `text.length()`，本身已把空格计入（`" "`、`"A "` 都会触发 ≥2 宽度）。
- 贴图水平方向使用 **advance（步进宽度）**，使空格这类无墨字符也占据宽度、正常显示；垂直方向仍用 visual bounds 保持居中。

---

## 5. 字体：优先西文字体 + 逐字符回退

**需求**：
- 将变量 `trafficaFont` 重命名为 `roadmarkFont`。
- 优先采用 `fonts/roadmark_en.ttf`（变量 `roadmarkEnFont`）——它是只含西文字符的字体。
- 当出现该西文字体不包含的字符时（如中文），该字符继续使用 `roadmarkFont`。

**实现**（`RenderRoadmarkText`）：

字段与加载（构造函数）：

```java
private static Font roadmarkFont;   // 完整字体（roadmark.ttf），用于西文字体不包含的字符
private static Font roadmarkEnFont; // 仅西文字体（roadmark_en.ttf），优先使用
```

- 两个字体分别从 `nebulaecraft:fonts/roadmark.ttf` 和 `nebulaecraft:fonts/roadmark_en.ttf` 加载。
- `roadmarkFont` 加载失败时回退到内置 `SansSerif`。
- `roadmarkEnFont` 加载失败时置为 `null`，渲染时自动全部使用 `roadmarkFont`。

逐字符字体优选（`generateTexture`），改用 `AttributedString` + `TextLayout`：

```java
java.awt.Font enFont = (roadmarkEnFont != null) ? roadmarkEnFont.deriveFont(100f) : null;
java.awt.Font cnFont = roadmarkFont.deriveFont(100f);

java.text.AttributedString as = new java.text.AttributedString(text);
for (int i = 0; i < text.length(); i++) {
    char c = text.charAt(i);
    java.awt.Font use = (enFont != null && enFont.canDisplay(c)) ? enFont : cnFont;
    as.addAttribute(java.awt.font.TextAttribute.FONT, use, i, i + 1);
}

java.awt.font.TextLayout layout = new java.awt.font.TextLayout(as.getIterator(), frc);
java.awt.geom.Rectangle2D visual = layout.getBounds(); // 垂直居中用
double advance = layout.getAdvance();                  // 水平铺满用（含空格）
```

- 每个字符优先用西文字体；西文字体 `canDisplay(c)` 为 `false` 的字符回退到 `roadmarkFont`。
- 同一标识可中西混排。

---

## 备注

准心选择框是否实时刷新取决于客户端何时重新查询 `getBoundingBox`——通常移动准心或方块更新时刷新。改完文字后准心移开再移回即可看到新框。
