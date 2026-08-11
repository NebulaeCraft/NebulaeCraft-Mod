# “Hello World!” 玩家飞行速度存档修改记录

> 日期：2026-08-11  
> 项目：NebulaeCraft（Minecraft 1.12.2 Forge / Java 8）  
> 目标：将开发存档 “Hello World!” 中主机玩家的飞行速度调整为原来的 1.5 倍。

## 1. 需求与存档位置

目标存档位于：

```text
run/saves/Hello World!/
```

玩家能力保存在 NBT 的 `abilities` 复合标签中，飞行速度字段为浮点数 `flySpeed`。

修改前读取结果：

```text
flySpeed = 0.05
```

按 1.5 倍计算后的目标值为：

```text
0.05 × 1.5 = 0.075
```

## 2. 开发客户端随机玩家问题

ForgeGradle 测试客户端每次启动时可能使用新的 `Player###` 名称和离线 UUID。因此，不能只依赖某一个 UUID 对应的 `playerdata/*.dat` 文件来保存主机玩家状态。

Minecraft 1.12.2 的 `PlayerList.readPlayerDataFromFile` 会在玩家名称等于集成服务器所有者时，优先读取世界信息中的主机玩家 NBT，也就是：

```text
level.dat -> Data -> Player
```

所以即使下次启动生成新的测试玩家名称或 UUID，集成服务器仍会从 `level.dat` 加载主机玩家能力。此次以 `level.dat` 为主要修改目标，同时同步最近一次测试玩家的独立数据，避免两处状态不一致。

最近一次进入存档的测试玩家为：

```text
名称：Player581
UUID：05a2b883-672c-35b9-82ec-eccc4e0cabc2
```

该玩家由 `run/logs/latest.log`、`run/usercache.json` 和玩家数据文件的修改时间共同确认。

## 3. 修改内容

修改了以下两个 gzip 压缩 NBT 文件中的 `abilities.flySpeed`：

```text
run/saves/Hello World!/level.dat
run/saves/Hello World!/playerdata/05a2b883-672c-35b9-82ec-eccc4e0cabc2.dat
```

两处修改结果一致：

```text
修改前：0.050000001
修改后：0.075000003
```

这里显示的末尾小数差异来自 IEEE 754 单精度浮点表示，逻辑值分别是 `0.05F` 和 `0.075F`。

此次只修改了存档数据，没有修改模组 Java 源码。飞行权限本身也没有变化；`flySpeed` 只控制玩家处于可飞行状态时的移动速度。

## 4. 备份

修改前创建了以下备份：

```text
run/saves/Hello World!/level.dat.codex-backup-20260811-0905
run/saves/Hello World!/playerdata/05a2b883-672c-35b9-82ec-eccc4e0cabc2.dat.codex-backup-20260811-0905
```

这些扩展名不属于 Minecraft 正常读取的存档文件，不会被游戏加载。

如需恢复，在游戏和集成服务器完全退出后，分别用对应备份覆盖 `level.dat` 和玩家 `.dat` 文件即可。恢复前建议另存一份当前文件，以免丢失修改之后的玩家进度。

## 5. 验证结果

完成修改后进行了以下验证：

- 重新解析两个 NBT 文件，确认 `abilities.flySpeed` 均为 `0.075000003`。
- 使用 `gzip -t` 检查两个文件，压缩数据完整性均正常。
- 修改前后的 SHA-256 不同，确认新数据已经写入。
- 修改操作是在最新日志明确记录集成服务器停止之后进行的，避免运行中的服务器回写旧数据。

本次属于存档数据修改，没有源代码变更，因此无需执行 Gradle 编译测试。
