# 道路创造模式物品栏排序

日期：2026-10-22

## 规则

`TabNebulaecraftRoad` 对 `nebulaecraft:asphalt` 开头的 ID 优先按颜色分组：

1. ID 不含 `white` 或 `yellow` 的普通组。
2. ID 含 `white` 的白色组。
3. ID 含 `yellow` 的黄色组。

关键词可以位于 ID 的任意位置，例如 `asphalt_double_yellow_line` 和
`asphalt_parallel_yellow_full` 都归入黄色组。每组内沿用现有字母自然排序，
同一 ID 的物品再按 metadata 排序。

其他物品和道路标志沿用原有排序规则。修改仅作用于客户端创造模式列表展示，
适用于单人与多人游戏。

## 验证

使用本机 JDK 8 执行 `./gradlew --offline compileJava`，结果为 `BUILD SUCCESSFUL`。
