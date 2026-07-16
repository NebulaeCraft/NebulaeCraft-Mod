# 版本号单一来源与 JAR 命名调整记录

> 日期：2026-07-16  
> 项目：NebulaeCraft（Minecraft 1.12.2 Forge / Java 8）

本文记录将模组版本号收敛为单一来源，并统一发布 JAR 文件名的修改过程。

## 1. 需求

- 修改版本号时，只修改 `NebulaecraftMod.java`。
- `mcmod.info` 中的版本号随构建自动更新。
- Gradle 项目版本和 JAR 文件版本使用同一个值。
- 构建出的文件名格式为：

```text
NebulaeCraft-1.12.2-版本号.jar
```

## 2. 修改前的问题

版本号原先分别硬编码在两个位置：

```text
src/main/java/net/kuina/nebulaecraft/NebulaecraftMod.java
src/main/resources/mcmod.info
```

其中 Java 常量和 `mcmod.info` 都是 `2.23`，每次发布时需要同步修改。此外，`build.gradle` 中的项目版本仍固定为 `1.0`，JAR 基础名固定为 `modid`，因此旧产物名为：

```text
modid-1.0.jar
```

## 3. 版本号单一来源

唯一需要手动维护的版本号位于：

```java
// src/main/java/net/kuina/nebulaecraft/NebulaecraftMod.java
public static final String VERSION = "2.23";
```

`build.gradle` 在配置阶段读取该 Java 文件，并从 `VERSION` 常量中提取版本号：

```groovy
def modMainSource = file('src/main/java/net/kuina/nebulaecraft/NebulaecraftMod.java')
def versionMatcher = modMainSource.getText('UTF-8') =~ /public\s+static\s+final\s+String\s+VERSION\s*=\s*"([^"]+)"/
if (!versionMatcher.find()) {
    throw new GradleException("Could not find the VERSION constant in ${modMainSource}")
}

version = versionMatcher.group(1)
```

如果常量被删除或改成无法识别的格式，Gradle 会直接报错，避免生成版本信息不正确的发布文件。

## 4. 自动生成 mcmod.info 版本

源码中的 `mcmod.info` 不再保存具体版本号，而是使用占位符：

```json
"version": "${version}"
```

Gradle 的 `processResources` 任务在构建时用项目版本替换该占位符：

```groovy
processResources {
    inputs.property 'version', project.version

    filesMatching('mcmod.info') {
        expand 'version': project.version
    }
}
```

以 `VERSION = "2.23"` 为例，生成到构建目录中的 `mcmod.info` 内容为：

```json
"version": "2.23"
```

## 5. JAR 文件命名

`build.gradle` 的 JAR 基础名修改为：

```groovy
archivesBaseName = 'NebulaeCraft-1.12.2'
```

Gradle 会自动在基础名后附加从 Java 常量读取的项目版本，因此当前构建产物为：

```text
build/libs/NebulaeCraft-1.12.2-2.23.jar
```

以后如果将 Java 常量改为：

```java
public static final String VERSION = "2.24";
```

再次构建后将自动得到：

```text
NebulaeCraft-1.12.2-2.24.jar
```

同时，模组注解、`mcmod.info`、Gradle 项目版本、JAR Manifest 中的 `Implementation-Version` 和 JAR 文件名都会使用 `2.24`。

## 6. 后续发布流程

发布新版本时只需：

1. 修改 `NebulaecraftMod.java` 中的 `VERSION` 常量。
2. 使用 Java 8 执行 JAR 构建。

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home ./gradlew jar
```

产物位于：

```text
build/libs/
```

## 7. 验证结果

本次修改已完成以下验证：

- Gradle 读取到的项目版本为 `2.23`。
- `processResources` 生成的 `mcmod.info` 版本为 `2.23`。
- Java 8 `compileJava` 编译成功。
- `jar` 和 `reobfJar` 任务成功。
- 最终生成 `NebulaeCraft-1.12.2-2.23.jar`。
- `git diff --check` 未发现格式错误。

## 8. 涉及文件

```text
build.gradle
src/main/java/net/kuina/nebulaecraft/NebulaecraftMod.java
src/main/resources/mcmod.info
docs/version-and-jar-naming.md
```
