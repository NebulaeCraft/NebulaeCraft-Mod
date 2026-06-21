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

**修复**：改用 `GlyphVector` 的 **实际像素轮廓边界（visual bounds）** 来做垂直定位与缩放，使笔画本身正好铺满贴图，从而真正居中。

```java
java.awt.font.FontRenderContext frc = g2d.getFontRenderContext();
java.awt.font.GlyphVector gv = font.createGlyphVector(frc, text);
java.awt.geom.Rectangle2D logical = gv.getLogicalBounds();
java.awt.geom.Rectangle2D visual  = gv.getVisualBounds();

double scaleX = width  / logical.getWidth();   // 水平用 logical
double scaleY = height / visual.getHeight();   // 垂直用 visual
g2d.scale(scaleX, scaleY);
g2d.drawGlyphVector(gv, (float) -logical.getX(), (float) -visual.getY());
```

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
- 贴图水平方向使用 `getLogicalBounds()`（advance 宽度），使空格这类无墨字符也占据宽度、正常显示；垂直方向仍用 `getVisualBounds()` 保持居中。

---

## 备注

准心选择框是否实时刷新取决于客户端何时重新查询 `getBoundingBox`——通常移动准心或方块更新时刷新。改完文字后准心移开再移回即可看到新框。
