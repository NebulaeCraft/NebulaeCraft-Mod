# 原生发光贴图替换 OptiFine

日期：2026-10-06。环境：Minecraft 1.12.2、Forge 14.23.5.2864、Java 8。

## 资源迁移

原来的 33 张普通贴图均为完全透明图片，实际内容位于同名 `_e.png`。
将每张 `_e.png` 原样覆盖对应普通 `.png`，并删除 `_e.png` 和模组内的
`assets/minecraft/optifine/emissive.properties`。迁移逐一检查 SHA-256，保留图片内容、
分辨率和透明度。模型 JSON 原本就引用普通名称，无须修改。

## 客户端实现

- `EmissiveModels` 在 `ModelBakeEvent` 中按贴图名称识别发光模型，覆盖方块的全部烘焙变体
  和物品模型。清单包含原有全部 33 张发光贴图，不按方块 ID 单独注册。
- 发光面沿用原几何体、UV、法线和透明度，亮度坐标为 `(240, 240)`，去除烘焙进顶点
  RGB 的方向阴影并关闭漫反射。普通面保持原顶点和光照设置。
- `EmissiveBlockRenderer` 包装现有方块渲染器：普通面仍交由原渲染器处理，发光面直接
  写入同一个区块缓冲，绕过环境遮蔽和环境光照乘算。使用原方块渲染层、朝向、面剔除和
  模型偏移，不新增覆盖面、TileEntity 或逐帧世界绘制。
- Forge 没有方块渲染器 setter，因此在客户端 `postInit` 用 Forge 的反射工具和 SRG
  字段名 `field_175027_c` 安装包装器。开发和发布环境共用同一映射入口。
- 物品使用 Forge 现有的 `renderLitItem` 支持，并保留物品变换和模型 override。
  此功能要求默认开启的 Forge 客户端配置 `allowEmissiveItems=true`；世界中的发光面
  不依赖 `forgeLightPipelineEnabled` 设置。
- 每次资源重载重新创建模型包装和缓存；面列表缓存按对象身份识别、使用弱键并限定大小。
  区块编译使用线程独立的写入器，避免多人客户端或并行区块编译共享可变顶点缓冲。

所有新增入口都在客户端代理中注册。服务器不加载渲染类，也不增加网络包；方块 ID、
metadata、朝向以及 `setLightLevel` 提供的世界照明均沿用原实现。

## 覆盖范围

按现有模型贴图引用统计，共涉及 124 个方块模型文件、87 个方块 ID：

- 灯具：`lamp`、`cubic_light`、`flat_light`、`pot_light`、`strip_light`、`tunnel_light`、
  `streetlight`、`lantern`、`platform_security_lightbar_1`、`platform_security_lightbar_2`。
- 标识和设备：`exit`、`exit_b`、`device_apple_imac`、`device_ms_surface`、`psl_goa2`、`psl_goa4`。
- 24 个使用发光贴图的铁路信号方块。
- `tdt_0` 到 `tdt_40`，共 41 个方块。
- 交通灯：`trafficlight_1` 到 `trafficlight_4`、`trafficlight_off`、`trafficlight_ped`。

上述名称是现有资源的覆盖统计，不是代码中的方块白名单。以后新模型引用这些贴图时也会
自动获得发光效果。新增发光贴图时，需要更新 `EmissiveModels.createTextureNames()`。

## 验证

- JDK 8：`gradlew.bat --offline compileJava processResources` 编译通过。
- JDK 8：`gradlew.bat --offline build` 构建及重混淆通过，产物为
  `build/libs/NebulaeCraft-1.12.2-2.26.jar`；核对 JAR 内 33 张贴图哈希、渲染类和旧资源删除情况。
- 33 张迁移后的源贴图和构建资源均与迁移前 `_e.png` 的 SHA-256 一致。
- 源资源和构建资源没有残留 `_e.png` 或旧的 OptiFine 发光配置。
- 本次按项目要求做编译和资源检查，未进行游戏内视觉或 OptiFine/光影包兼容性验证。
  该实现提供贴图自身的最大亮度显示，不增加光影包的泛光或彩色光照。

