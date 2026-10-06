# VS Code 使用统一源码及构建输出

日期：2026-10-06。项目：NebulaeCraft，Java 8 / Forge 1.12.2。

## 目录含义

- `src/main/java`：唯一的 Java 源码，编辑和断点调试直接使用这里的文件。
- `src/main/resources`：唯一的模型、贴图和其他源资源，Gradle 从这里读取。
- `build/sourcesSets/main`：统一的编译类及处理后的资源输出。Java 运行时需要 `.class`，
  不能将 `.java` 文件直接当作已编译类执行；资源处理还会展开 `mcmod.info` 的版本号。
- `build/sourcesSets/test`：测试编译输出。

原来的 VS Code Java / Buildship 输出为 `bin/main`，与 Gradle 的输出不一致。
此次移除 `bin` 和临时同步机制，后续只编辑 `src`，所有生成输出均放在 `build`。

## 配置

1. `build.gradle` 设置 Eclipse/Buildship 默认输出和每个源码条目的输出目录，与 Gradle
   `sourceSets` 一致。生成的 Eclipse 类路径中，`src/main/java` 和 `src/main/resources`
   均指向 `build/sourcesSets/main`。
2. 本机 `.vscode/launch.json` 的客户端和服务端配置都指定调试源码 `src/main/java`，
   `MOD_CLASSES` 指向 `build/sourcesSets/main`，并显式排除旧 `bin/main`、`bin/test` 类路径。
3. 调试使用带既有 Railcraft 访问修正的 `build/dev` Forge JAR，排除旧自动解析的
   `build/fg_cache` Forge JAR；启动前任务负责生成该 JAR。
4. `.vscode/tasks.json` 的客户端任务运行 `classes prepareRunClient prepareDevForgeJar`，
   服务端任务运行 `classes prepareRunServer prepareDevForgeJar`。两者使用本机 JDK 8，
   每次启动都会先从源码编译并处理资源。
5. `.vscode/settings.json` 关闭 Java 插件独立自动构建和调试前强制构建，让 Gradle 负责
   编译，避免原 Java 工作区缓存继续生成 `bin`。Gradle 导入仍启用，源码浏览和调试仍可用。

`.vscode` 和生成的 `.classpath` 均被现有 `.gitignore` 忽略，本机调试配置不是发布资源。
重新加载 VS Code 窗口，让已有 Java 工作区重新读取配置；不要再执行旧的同步任务。

## 验证

JDK 8 执行：

```text
gradlew.bat --offline classes prepareRunClient prepareRunServer prepareDevForgeJar eclipseClasspath
```

执行成功，生成类路径中的主源码和资源条目均使用统一输出目录。
核对 VS Code JSON、启动任务和 `MOD_CLASSES` 配置后，仅删除项目根目录的生成目录 `bin`。
