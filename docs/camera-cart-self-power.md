# 拍摄用车自带动力与 Railcraft 按键控制

日期：2026-11-10

## 目标

让拍摄用车（`nebulaecraft:cameracart`）像 Railcraft 的永动机车一样无需燃料自行行驶，
并直接使用 Railcraft 已有的机车按键，不新增按键。

## 方案

不继承 Railcraft 的 `EntityLocomotive`，也不引用任何 Railcraft Java 类：

- 本项目没有 Railcraft 的编译期依赖（`run/mods` 中是 SRG 混淆包，且该目录被忽略）。
- 继承机车类会带来相撞爆炸、撞伤生物、不可乘坐、机车模型渲染等副作用。

因此在 `CameraCart.EntityCameraCart` 中复刻永动机车的动力模型
（对应 Railcraft `EntityLocomotive.applyDrag()`），动力只在服务端计算：

- 每 tick 水平速度乘以 `0.9`；运行时沿 `rotationYaw` 施加 `0.15` 的推力
  （Railcraft 默认马力 15 × 0.01），倒车时取反。
- 档位是水平合速度上限，按向量长度裁剪：

| 档位 | 上限 | 约合 |
|---|---|---|
| 1 | `0.1 格/tick` | 2 格/秒 |
| 2 | `0.2 格/tick` | 4 格/秒 |
| 3（默认） | `0.3 格/tick` | 6 格/秒 |
| 4 | 不额外限速 | 由轨道上限决定 |

- 倒车时最高 3 档，与机车一致。
- 覆写 `moveMinecartOnRail`：去掉原版载人时 ×0.75 的减速，使载人速度与上表一致；
  并在移动前再按档位裁剪一次，Railcraft 强化坡道处理器写入的固定速度不会冲破低档上限。
- `isPoweredCart()` 返回 `true`。
- 乘客离车时自动停机。该车没有 GUI，只能由乘坐者操作，这样可避免无人车一直行驶。
- 运行状态、档位、方向写入实体 NBT；旧存档中的拍摄用车读入后为停机、3 档、前进。
- 用物品放置时，车头朝向取玩家的水平朝向，「前进」即放置时面对的方向。
  发射器放置保持默认朝向，可用换向键调整。

## 按键

`client/CameraCartKeyHandler` 在 `KeyInputEvent` 中按描述名查找 Railcraft 注册的按键对象：

| 描述名 | 默认键 | 作用 |
|---|---|---|
| `keybind.railcraft.loco.mode` | `Alt+M` | 运行 / 停机 |
| `keybind.railcraft.loco.faster` | `Alt+.` | 升档 |
| `keybind.railcraft.loco.slower` | `Alt+,` | 降档 |
| `keybind.railcraft.loco.reverse` | `Alt+/` | 换向 |

玩家在 Railcraft 分类下改键后同样生效。判定使用 `KeyBinding.isActiveAndMatches(本次按键)`，
不调用 `isPressed()`，因为 Railcraft 自己的处理器会在同一事件中把它消费掉。
鸣笛键不处理。

操作后不显示任何文字提示（最初实现的快捷栏状态提示已按要求移除）。

## 行驶方向

推力始终沿实体的 `rotationYaw` 施加：`(cos(yaw), sin(yaw)) × 0.15`，倒车时取反。

- 用物品放置时，`rotationYaw` 取玩家水平朝向；发射器放置时为默认值 0（朝东，+X）。
- 行驶中，原版 `EntityMinecart.onUpdate` 按位移方向重算 `rotationYaw`，并在与上一 tick
  相差约 180° 时翻转回来，所以这个「车头朝向」在来回行驶时保持不变，只会随弯道转动。
- 推力不沿轨道时，原版 `moveAlongTrack` 会保留速度大小并把方向改到轨道上，
  按推力与轨道方向的点积选择两个方向之一；垂直时取该轨道形状的默认方向，
  随后车头朝向会在几 tick 内稳定到轨道上。
- 换向键只切换推力的正负号，不转动车头。

## 多人游戏

按键经 `PacketCameraCartControl`（客户端 → 服务端，1 字节动作）发送。
服务端在主线程中校验 `player.getRidingEntity()` 是拍摄用车后才执行，
因此每名玩家只能控制自己正在乘坐的那一辆。新消息追加在注册列表末尾，已有消息 ID 不变。

## 与永动机车的差异

- 不响应 Railcraft 针对机车的轨道套件（机车轨道、节流轨道、鸣笛轨道），没有机车 GUI。
- 马力固定为 15，不读取 Railcraft 配置。
- 强化斜线上，Railcraft 只对含运行机车的列车做速度维持；拍摄用车靠自身推力保持速度。
- 乘坐拍摄用车时 Railcraft 自己的按键包仍会发出。若拍摄用车与真机车联挂，
  同一组按键会同时控制两者。

## 验证

使用本机 JDK 8 执行 `./gradlew --offline build`，结果为 `BUILD SUCCESSFUL`。
未做游戏内实测，建议复测：放车、上车、启动、换档、换向、下车自动停机、
重进存档后状态保留，以及专用服务器上两名玩家各自控制自己的车。
