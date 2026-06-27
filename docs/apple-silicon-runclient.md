# Apple Silicon 原生 ARM 运行 Forge 1.12.2 记录

> 日期：2026-06-27
> 目标：在 Apple Silicon 上使用 ARM64 Java 8 原生运行 Minecraft 1.12.2 Forge `runClient`，同时保持同步回 x64 Windows 后仍可编译和启动。

本文记录本次从 Windows/IntelliJ IDEA 项目同步到 macOS Apple Silicon 后，`runClient` 相关问题的排查和修复。

---

## 1. 初始目标

项目最初在 Windows 上的 JetBrains IDEA 中运行。同步到 macOS Apple Silicon 后，希望继续使用原生 ARM64 Java 8，而不是切换到 Rosetta/x86_64。

本机开发 JDK 固定为：

```text
/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
```

不切到 Java 17/25，因为当前项目使用 Gradle 5.6.4，旧 Groovy 在较新 JDK 上可能出现兼容性失败。

---

## 2. LWJGL/JInput native 架构问题

macOS ARM64 JVM 启动 ForgeGradle `runClient` 时，默认解出的 LWJGL 2 / JInput macOS native 是 x86_64，因此会出现类似问题：

```text
missing compatible architecture
have 'x86_64', need 'arm64'
```

处理方式是在 `build.gradle` 中加入仅对 macOS ARM64 生效的配置：

- `macosArmNatives` configuration
- 从 `libs/macos-arm64-natives` 读取 ARM64 native jar
- `copyMacosArmNatives` task 将以下文件覆盖到 `build/natives`
  - `liblwjgl.dylib`
  - `openal.dylib`
  - `libjinput-osx.jnilib`
- 让该 task 在 `extractNatives` / `prepareRunClient` 后执行

同时仅在 macOS ARM64 下强制 LWJGL Java jar 到 `2.9.4-nightly-20150209`，并将 JNA 强制到 `5.13.0`，降低 native/Java 侧版本不一致风险。

关键点：这些 Gradle 分支由 `isMacosArm` 包裹，Windows x64 不会执行 ARM native 覆盖，也不会被强制使用 macOS ARM native。

---

## 3. IDEA 生成的 MainClient task 缺少环境变量

从 IDEA 运行时曾出现：

```text
Exception in thread "main" java.lang.IllegalArgumentException: Must specify mainClass environment variable
    at net.minecraftforge.legacydev.Main.start(Main.java:57)
    at net.minecraftforge.legacydev.MainClient.main(MainClient.java:29)
```

这是 IDEA/Gradle 生成的 JavaExec task 没拿到 ForgeGradle 需要的环境变量导致的。

已在 `build.gradle` 中对 `net.minecraftforge.legacydev.MainClient.main()` 这类 JavaExec task 补齐：

- `mainClass=net.minecraft.launchwrapper.Launch`
- `MCP_TO_SRG`
- `MOD_CLASSES`
- `MCP_MAPPINGS`
- `FORGE_VERSION`
- `assetIndex`
- `assetDirectory`
- `nativesDirectory`
- `FORGE_GROUP`
- `tweakClass`
- `MC_VERSION`

这样即使 IDEA 走这个生成 task，也能获得 ForgeGradle 启动所需环境。

---

## 4. macOS ARM64 Narrator 崩溃

LWJGL native 修好后，客户端继续启动，但又在 Mojang Narrator 相关依赖处失败。

原因是 Minecraft 1.12.2 的 macOS Narrator 会加载 `ca.weblite:java-objc-bridge:1.0.0`，其中 native `libjcocoa.dylib` 是 x86_64，不能被 ARM64 JVM 加载。

处理方式是在开发源码里加入 `com.mojang.text2speech.Narrator` 覆盖类：

- 仅 macOS ARM64 返回 no-op narrator
- Windows 仍返回 `NarratorWindows`
- Linux 仍返回 `NarratorLinux`
- macOS x64 仍尝试使用 `NarratorOSX`
- `jar` task 排除 `com/mojang/text2speech/**`，避免该开发补丁进入最终发布 jar

---

## 5. Windows 编译兼容性修复

同步回 x64 Windows 后，`compileJava` 曾失败：

```text
D:\User\Kuina\Project\NebulaeCraft\src\main\java\com\mojang\text2speech\Narrator.java:43: 错误: 无法访问NSObject
            return new NarratorOSX();
                   ^
  找不到ca.weblite.objc.NSObject的类文件
```

原因是虽然 Windows 运行时不会走 macOS 分支，但 Java 编译器会解析直接引用的 `NarratorOSX` 类型，并继续追到它的 objc bridge 父依赖。

最终修复是把 macOS x64 的 `NarratorOSX` 构造改为运行期反射加载：

```java
static Narrator createMacosNarrator() {
    try {
        return (Narrator) Class.forName("com.mojang.text2speech.NarratorOSX").newInstance();
    } catch (ReflectiveOperationException | LinkageError e) {
        return new NarratorDummy();
    }
}
```

这样 Windows 编译期不再需要解析 `ca.weblite.objc.NSObject`，但 macOS x64 运行时仍可在依赖存在时使用原版 `NarratorOSX`。

---

## 6. 验证记录

macOS ARM64 上执行：

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home ./gradlew prepareRunClient
```

验证 native 架构：

```bash
file build/natives/liblwjgl.dylib
file build/natives/openal.dylib
file build/natives/libjinput-osx.jnilib
```

期望结果：

- `liblwjgl.dylib` 包含 `arm64`
- `openal.dylib` 包含 `arm64`
- `libjinput-osx.jnilib` 为包含 `arm64` 的 universal 或 ARM64 native

本机已验证：

- `compileJava` 成功
- `jar` 成功
- `runClient` 已进入 Forge/Minecraft 启动流程，显示 Apple M 系列 OpenGL Renderer，不再卡在 x86_64 native 架构错误
- 发布 jar 中没有 `com/mojang/text2speech` 覆盖类

Windows x64 侧预期：

- `isMacosArm` 为 `false`，不会复制 macOS ARM64 native
- Windows Narrator 路径不依赖 `NarratorOSX`
- `compileJava` 不再因 `ca.weblite.objc.NSObject` 缺失失败

---

## 7. 相关文件

- `build.gradle`
- `libs/macos-arm64-natives/lwjgl-platform-natives-osx-arm64-2.9.4-nightly-20150209.jar`
- `libs/macos-arm64-natives/jinput-platform-natives-osx-arm64-2.0.5.jar`
- `src/main/java/com/mojang/text2speech/Narrator.java`

---

## 备注

`.idea/runConfigurations` 曾因为文件所有者问题无法直接修改。如果以后需要调整 IDEA run configuration，可先在 macOS 本机执行：

```bash
sudo chown -R kuina:staff /Users/kuina/Documents/Codex/NebulaeCraft/.idea/runConfigurations
```

当前主要逻辑已放在 `build.gradle`，不依赖必须修改 IDEA 配置文件。
