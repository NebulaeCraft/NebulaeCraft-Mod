# Block model 碰撞框与物品 GUI 调整记录

> 日期：2026-06-27
> 涉及范围：
> - `BlockClShieldEnddoor`
> - `BlockScreenDoorTop2`
> - `BlockScreenDoorEnddoor`
> - `BlockSubwayPhone`
> - `cl_shield_enddoor` / `cl_half_shield_enddoor` item model GUI 显示位置

本文记录本次对话中围绕 block model 实际尺寸调整 `getBoundingBox`，以及修正部分端门物品栏显示位置的修改。

---

## 1. 调整原则

Minecraft block model 的 `from` / `to` 坐标以 16 为一个方块单位，因此换算规则为：

```text
AABB 坐标 = block model 坐标 / 16
```

对于有 `facing` 的方块，先按 blockstate 中 `facing=north` 对应的原始模型范围建立基础 `AxisAlignedBB`，再按 blockstate 的 `y` 旋转规则转换：

- `NORTH`：返回基础框
- `SOUTH`：绕方块中心旋转 180 度
- `EAST`：绕方块中心旋转 90 度
- `WEST`：绕方块中心旋转 270 度

部分模型元素会超出当前方块范围，例如角件的 X 为 `-4..16` 或 `0..20`，对应 AABB 会出现负值或大于 1 的值。为了让准心选择框贴合实际模型，保留这些越界范围。

---

## 2. `BlockClShieldEnddoor`

涉及模型：

- `models/block/cl_shield_enddoor_0.json`
- `models/block/cl_shield_enddoor_1.json`

模型整体范围：

| 坐标轴 | block model 范围 | AABB 范围 |
|--------|------------------|-----------|
| X      | `0..16`          | `0..1`    |
| Y      | `0..32`          | `0..2`    |
| Z      | `7.95..9`        | `0.496875..0.5625` |

`getBoundingBox` 已按上述范围调整，并根据 `FACING` 旋转。

---

## 3. `cl_shield_enddoor` / `cl_half_shield_enddoor` item GUI

问题：这两类端门物品在物品栏里显示太靠上。

调整文件：

- `models/item/cl_shield_enddoor_subtype0.json`
- `models/item/cl_shield_enddoor_subtype1.json`
- `models/item/cl_half_shield_enddoor_subtype0.json`
- `models/item/cl_half_shield_enddoor_subtype1.json`

GUI translation 调整：

| item model | 调整后 `display.gui.translation` |
|------------|----------------------------------|
| `cl_shield_enddoor_subtype0/1` | `[0, -1.9, 0]` |
| `cl_half_shield_enddoor_subtype0/1` | `[0, -1, 0]` |

---

## 4. `BlockScreenDoorTop2`

涉及 blockstate：

- `blockstates/screen_door_top2.json`

涉及模型：

- `screen_door_topmiddle`
- `screen_door_topside_1`
- `screen_door_topside_2`
- `screen_door_top_enddoor`

基础朝北时的模型范围：

| subtype | 模型 | block model 范围 | AABB |
|---------|------|------------------|------|
| `subtype0` | `screen_door_topmiddle` | X `0..16`, Y `0..16`, Z `0..4` | `(0, 0, 0) -> (1, 1, 0.25)` |
| `subtype1` | `screen_door_topside_1` | X `-4..16`, Y `0..16`, Z `0..12` | `(-0.25, 0, 0) -> (1, 1, 0.75)` |
| `subtype2` | `screen_door_topside_2` | X `0..20`, Y `0..16`, Z `0..12` | `(0, 0, 0) -> (1.25, 1, 0.75)` |
| `subtype3` | `screen_door_top_enddoor` | X `0..16`, Y `0..16`, Z `8..12` | `(0, 0, 0.5) -> (1, 1, 0.75)` |

本次修改让 `getBoundingBox` 先按 `SUBTYPE` 选基础框，再按 `FACING` 旋转。

---

## 5. `BlockScreenDoorEnddoor`

涉及 blockstate：

- `blockstates/screen_door_enddoor.json`

涉及模型：

- `screen_door_enddoor_0`
- `screen_door_enddoor_1`
- `screen_door_enddoor_2`
- `screen_door_enddoor_3`

基础朝北时的模型范围：

| subtype | 模型 | block model 范围 | AABB |
|---------|------|------------------|------|
| `subtype0` | `screen_door_enddoor_0` | X `0..16`, Y `0..32`, Z `7.95..10` | `(0, 0, 0.496875) -> (1, 2, 0.625)` |
| `subtype1` | `screen_door_enddoor_1` | X `0..16`, Y `0..32`, Z `7.95..10` | `(0, 0, 0.496875) -> (1, 2, 0.625)` |
| `subtype2` | `screen_door_enddoor_2` | X `0..16`, Y `0..24.5`, Z `8..10` | `(0, 0, 0.5) -> (1, 1.53125, 0.625)` |
| `subtype3` | `screen_door_enddoor_3` | X `0..16`, Y `0..21`, Z `8..9` | `(0, 0, 0.5) -> (1, 1.3125, 0.5625)` |

`getBoundingBox` 已按 subtype 区分高度和厚度，再按 `FACING` 旋转。

---

## 6. `BlockSubwayPhone`

涉及 blockstate：

- `blockstates/subway_phone.json`

涉及模型：

- `subway_phone_0`
- `subway_phone_1`

`subway_phone` 模型中有 `-45`、`45`、`-22.5`、`22.5` 等旋转元素，因此统计范围时需要考虑旋转后的元素顶点，而不只看原始 `from` / `to`。

基础朝北时的最终包围范围：

| subtype | 模型 | AABB |
|---------|------|------|
| `subtype0` | `subway_phone_0` | `(0.40625, 0.21875, 0.90625) -> (0.59375, 0.6875, 1.003125)` |
| `subtype1` | `subway_phone_1` | `(0.38125, 0.30625, 0.875) -> (0.6125, 0.69375, 1)` |

`getBoundingBox` 已按 subtype 选小体积包围盒，并随 `FACING` 旋转。

---

## 7. 验证

每次 Java 代码调整后均运行：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home ./gradlew compileJava
```

结果：`BUILD SUCCESSFUL`。

端门 item model 的 JSON 也使用 `python3 -m json.tool` 做过解析验证。

---

## 8. 注意事项

- 本次调整的是 `getBoundingBox`，主要影响准心选择框/交互选中范围。
- 如果需要同步调整实体碰撞阻挡行为，应另外检查对应类是否覆写 `getCollisionBoundingBox` 或是否依赖默认碰撞框。
- 对于越界模型，AABB 保留小于 `0` 或大于 `1` 的坐标是有意的，用于贴合实际视觉模型。
