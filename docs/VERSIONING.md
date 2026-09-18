# SimpleOffhand 版本差异

## 分支与坐标

| 分支 | MC | 平台版本 | `*_version_range` | JDK | Gradle |
| --- | --- | --- | --- | --- | --- |
| `master` | 26.3 | NeoForge 26.3.0.1-beta | `[26.3.0,)` | 25 | 9.6.1 |
| `26.2` | 26.2 | NeoForge 26.2.0.88 | `[26.2.0,)` | 25 | 9.6.1 |
| `26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | `[26.1.2,)` | 25 | 9.6.1 |
| `1.21.11` | 1.21.11 | NeoForge 21.11.45 | `[21.11,)` | 21 | 8.8 |
| `1.21.10` | 1.21.10 | NeoForge 21.10.64 | `[21.10,)` | 21 | 8.8 |
| `1.21.8` | 1.21.8 | NeoForge 21.8.54 | `[21.8,)` | 21 | 8.8 |
| `1.21.1` | 1.21.1 | NeoForge 21.1.250 | `[21.1,)` | 21 | 8.8 |
| `1.20.6` | 1.20.6 | NeoForge 20.6.141 | `[20.6,)` | 21 | 8.8 |
| `1.20.4` | 1.20.4 | NeoForge 20.4.251 | `[20.4,)` | 17 | 8.8 |
| `1.20.1` | 1.20.1 | **Forge** 47.1.106 | `[47.1.106,)` | 17 | 8.8 |

1.20.1 没有 NeoForge（NeoForge 从 1.20.2 起），用的是 Forge：制品坐标
`net.neoforged:forge:1.20.1-47.1.106`，构建插件也换成
`net.neoforged.moddev.legacyforge`，其扩展名是 `legacyForge`，版本必须写成
`enable { neoForgeVersion = ... }`（extension 上的 `version =` 走的是 `forgeVersion`）。

legacyforge 插件还有两处与新版插件不同：

- **run 类型只认 `server` / `data` / `client` / `gameTestServer` 四种**。
  照抄新版 `build.gradle` 里的 `clientData()` 会让 IDE 同步直接失败：
  `Failed for task ':prepareDataRun'. Trying to prepare unknown run: clientData`。
- **mixin 配置的注册方式不同**（见「1.20.1 的 mixin 未生效」一节）。

1.21.4 / 1.21.5 等不计划做。

## 1.20.1 的 mixin 注册方式（已解决）

**背景**：1.20.1 的开发环境用
`-Dfml.modFolders=simpleoffhand%%<classes>;simpleoffhand%%<resources>`
把 mod 以**目录形式**加载，**不是 jar**。目录没有 manifest，而 Forge 1.20.1 只从
manifest 的 `MixinConfigs` 读取 mixin 配置列表 —— 所以配置会被**静默忽略**
（不报错、也不生效）。其它分支能直接用 `mods.toml` 的 `[[mixins]]`，1.20.1 不行。

判定方法（可复现）：在注入方法开头插一条无条件日志，再在 `mixins.json` 的 `client`
列表里加一个不存在的类名。若日志 0 次调用、且不存在的类名也没报错，就说明配置根本没被读取。

### 需要的三项配置

1. **让 mixin 注解处理器跑起来**（它是生成 `refmap` / `mappings.tsrg` 的前提）：

   ```groovy
   dependencies {
       annotationProcessor files('libs/mixin-0.8.5.jar')
       // 处理器自身要用这三个
       annotationProcessor 'com.google.code.gson:gson:2.10'
       annotationProcessor 'com.google.guava:guava:31.1-jre'
       annotationProcessor 'org.ow2.asm:asm:9.5'
       annotationProcessor 'org.ow2.asm:asm-tree:9.5'
   }
   mixin.add(sourceSets.main, "${mod_id}.mixins.json")
   ```

   **注意：不存在单独的 `:processor` 制品**。处理器就打包在 `mixin-0.8.5.jar` 里
   （`META-INF/services/javax.annotation.processing.Processor` 注册了
   `MixinObfuscationProcessorInjection` / `MixinObfuscationProcessorTargets`），
   所以直接用运行时那个 jar 即可。少了它会在 `reobfJar` 阶段报
   `FileNotFoundException: build/mixin/<config>.mappings.tsrg`。

2. **所有 run 加 `--mixin.config`**（开发环境的注册靠它）：

   ```groovy
   runs.configureEach {
       programArgument '--mixin.config'
       programArgument "${mod_id}.mixins.json"
   }
   ```

   实测：去掉这个参数 mixin 就不生效，加上就生效。它不是诊断参数，是必需项。

3. **生产 jar 的 manifest 写 `MixinConfigs`**（发布产物靠它注册）：

   ```groovy
   jar {
       exclude "${mod_id}.mixins.json"   // mixin.add 会加处理后的那份，否则 duplicate entry
       manifest { attributes('MixinConfigs': "${mod_id}.mixins.json") }
   }
   ```

### 验证

`runClient` 进世界、空着副手看第一人称，日志出现这一行即为生效：

```
[Render thread/INFO] [SimpleOffhand/]: Offhand arm rendering active (injection applied).
```

### ⚠️ 发布 jar 的 mixin 尚未跑通（未解决）

**开发环境已验证可用，但 release jar 还不行**，发布前必须处理。已实测出的三点：

1. **`reobfJar` 不会重映射 mixin 类** —— jar 里仍是 named 名
   （`renderArmWithItem`、`ItemInHandRenderer`），而生产环境的目标类用的是 SRG 名
   （`m_109371_`）。所以生产**必须**靠 refmap 重映射。
2. **refmap 没有被生成** —— 处理器只产出了加工版 `mixins.json`（内含 `mappings`）与
   `mappings.tsrg`，没产出 `simpleoffhand.mixins.json.refmap.json`。
3. **`reobfJar` 会丢掉额外加进 jar 的文件** —— 因此"把加工版配置显式塞进 jar"的做法无效，
   final jar 里剩下的仍是 `resources` 那份原始配置（不含映射）。

排查时踩过的坑（供后续参考）：Gradle 的 `exclude` 是**模式匹配**，写
`"simpleoffhand.mixins.json"` 会把 `"simpleoffhand.mixins.json.refmap.json"` 一起匹配掉
（前者是后者的子串），要用正则或 `it.path ==` 精确匹配；另外 `exclude` 会作用于**所有来源**，
连自己后来加的那份也会被排除。

### 附带修的问题

- **`clientData()` 要改成 `data()`**：legacyforge 只认
  `server` / `data` / `client` / `gameTestServer`，写错会让 IDE 同步失败
  （`Trying to prepare unknown run: clientData`）。
- **`pack.mcmeta` 缺失**：会报 `Missing metadata in pack mod:simpleoffhand`，已补
  （`pack_format = 15`）。
- **`loaderVersion` 勘误**：早期误以为要用 `[4,)`；实测 `--fml.fmlVersion` 是 `47.2.2`，
  所以 `[47,)` 是对的。

## Mixin 类与方法

目标类：`net.minecraft.client.renderer.ItemInHandRenderer`（26.2 及更早）
/ `net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer`（26.3 起，见下）
，mixin 类固定是 `com.halfinity.mixin.ItemInHandRendererMixin`。

| 分支 | 注入目标 | 注入方法与 `renderPlayerArm` 的第 2 个参数 |
| --- | --- | --- |
| `master` (26.3) | `submitArmWithItem` | `SubmitNodeCollector` |
| `26.2` | `submitArmWithItem` | `SubmitNodeCollector` |
| `26.1.2` | `renderArmWithItem` | `SubmitNodeCollector` |
| `1.21.11` | `renderArmWithItem` | `SubmitNodeCollector` |
| `1.21.10` | `renderArmWithItem` | `SubmitNodeCollector` |
| `1.21.8` | `renderArmWithItem` | `MultiBufferSource` |
| `1.21.1` | `renderArmWithItem` | `MultiBufferSource` |
| `1.20.6` | `renderArmWithItem` | `MultiBufferSource` |
| `1.20.4` | `renderArmWithItem` | `MultiBufferSource` |
| `1.20.1` | `renderArmWithItem` | `MultiBufferSource` |

全部签名已用 `javap` 核对各分支 `build/moddev/artifacts/` 里的制品确认。

### 26.3 的重构

26.3 把第一人称手部渲染整个换成了新的渲染状态体系，**原来的 `ItemInHandRenderer` 已不存在**：

| | 26.2 及更早 | 26.3 |
| --- | --- | --- |
| 目标类 | `ItemInHandRenderer` | `FirstPersonHandsAndItemsRenderer` |
| `submitArmWithItem` 参数个数 | 10 | **11** |
| 玩家相关参数 | `AbstractClientPlayer` | `PlayerRenderState` + `FirstPersonHandsAndItemsRenderState` |
| `renderPlayerArm` | 6 参 | **7 参**（末尾多一个 `PlayerRenderState`） |
| 主手物品 | `player.getMainHandItem()` | `state.mainHandItem` |
| 瞄准 / 可见性 | `player.isScoping()` / `player.isInvisible()` | `state.isScoping` / `avatarRenderState.isInvisible` |
| 主手朝向 | `player.getMainArm()` | `avatarRenderState.mainArm` |

两个 state 类都在 `net.minecraft.client.renderer.state.level` 包下。注意
`PlayerRenderState` **自身没有** `mainArm` / `isInvisible`，要经它的
`avatarRenderState` 字段（可能为 `null`，必须判空）取。

## 平台 API 差异

| 分支 | 主类注解 | 配置 spec | 配置界面 |
| --- | --- | --- | --- |
| 26.x / 1.21.x | `@Mod(dist = Dist.CLIENT)` | `neoforge.common.ModConfigSpec` | 注册 `IConfigScreenFactory` + 内置 `ConfigurationScreen` |
| `1.20.6` | `@Mod(dist = Dist.CLIENT)` | `neoforge.common.ModConfigSpec` | 注册 `IConfigScreenFactory`，**界面自己写** |
| `1.20.4` | `@Mod` 无 `dist`，用 `@OnlyIn` | `neoforge.common.ModConfigSpec` | 注册 `ConfigScreenHandler.ConfigScreenFactory`，界面自己写 |
| `1.20.1` | `@Mod` 无 `dist`，用 `@OnlyIn` | `minecraftforge.common.ForgeConfigSpec` | 注册 `ConfigScreenHandler.ConfigScreenFactory`，界面自己写 |

**配置界面不会自动出现，必须显式注册一个工厂。** 反汇编 `ConfigScreenHandler` / `getScreenFactoryFor`
可以看到它的逻辑就是 `ModList.getModContainerById(modId).flatMap(c -> c.getCustomExtension(...))`
—— 只取模组自己注册的扩展点；没注册就没有「配置」按钮。1.20.1 / 1.20.4 / 1.20.6 三条分支的界面是同一份
`client/SimpleOffhandConfigScreen`，只有两处随版本变：配置界面的 import，以及
`Screen.renderBackground` 的签名（1.20.1 是单参 `(GuiGraphics)`，1.20.4 起是四参
`(GuiGraphics, int, int, float)`）。

其它：

| | |
| --- | --- |
| `ModContainer.registerConfig` | 1.21.x 有；1.20.6 有；1.20.4 / 1.20.1 **没有**（走 `ModLoadingContext.get().registerConfig`） |
| `defineList` | 1.21.x 是 4 参（带"新元素默认值"）；26.x / 1.20.x 是 3 参 |
| 标识符类 | 1.21.11 起是 `Identifier`；1.21.10 及更早是 `ResourceLocation` |
| 模组图标 | 26.2 用 `iconFile` + `bannerFile`；其余分支只能用 `logoFile` |
| 编译编码 | 中文注释必须显式 `options.encoding = 'UTF-8'`，否则 Windows 上 javac 用 GBK 读取会报不可映射字符 |

## 模组元数据（mods.toml）

**文件名写错会让模组静默不加载** —— FML 只会去找它认识的文件名，其余一律忽略，既不报错也不出现在模组列表里。

| 分支 | 模板文件名 | 依赖声明字段 | `loaderVersion` |
| --- | --- | --- | --- |
| `26.3`（master） | `neoforge.mods.toml` | `type = "required"` | `[12,)` |
| `26.2` / `26.1.2` / `1.21.x` | `neoforge.mods.toml` | `type = "required"` | `[4,)` 起即可 |
| `1.20.6` | `neoforge.mods.toml` | `type = "required"` | `[3.0.45,)` |
| `1.20.4` | **`mods.toml`** | `type = "required"` | `[2,)` |
| `1.20.1` | **`mods.toml`** | **`mandatory = true`** | `[47,)` |

三个容易踩的点：

1. **文件名分界在 1.20.6**：20.4 及更早只认 `mods.toml`（20.4.251 自己 jar 里就是 `mods.toml`），20.6 起改成 `neoforge.mods.toml`。
2. **依赖字段分界在 Forge / NeoForge**：1.20.1 的 Forge 只认 `mandatory = true`，用 `type` 报 `Missing required field mandatory`；而 NeoForge 20.4 反过来**拒绝** `mandatory`（`InvalidModFileException: Deprecated 'mandatory' field is used`），只认 `type = "required"`。
3. **`loaderVersion` 不是 FML 版本**：对 NeoForge 它是 **javafml 语言加载器版本**，取值等于该线的 FancyModLoader 版本（20.4 → `2.0`，20.6 → `3.0.45`，21.1 → `4.0.44`，21.8 → `9.0.18`，21.10/21.11 → `10.0.x`，26.2 → `11.x`，**26.3 → `12.0`**；而 1.20.1 的 Forge 用 FML 版本 `47.2.2`）。写小了会报 `Missing language javafml version [x,) wanted by main, found y`。

## 开发环境

各分支要求的 JDK 不同：

| 分支 | JDK |
| --- | --- |
| `26.3` / `26.2` / `26.1.2` | 25 |
| `1.21.x` / `1.20.6` | 21 |
| `1.20.4` / `1.20.1` | 17 |

实测（`gradlew build` 逐分支跑过）：**各分支在对应的 JDK 下全部构建成功**；同一分支换用另一个 JDK
也都能成功（Gradle 由 `toolchain` 决定编译用的 JDK，`gradle-daemon-jvm.properties` 决定守护进程用哪个）。
所以 CLI 侧不受 Gradle JVM 影响。

**但 IDE 侧只有一个 Gradle JVM 设置，且为工作区级、被所有分支共用**（`.idea/gradle.xml` 的 `gradleJvm`），
切换分支后这个设置不会跟着变。若同步失败，先确认它是否是当前分支对应的版本。

## 未解决

- **1.20.1 / 1.20.4 / 1.20.6 的配置界面没在游戏里点开过**：三条分支的界面是同一份自写实现，构建通过、
  模组能加载，但界面实际渲染与读写是否正常没有验证。
- **各分支的注入是否真的生效只验证过一部分**：`1.21.1` / `1.21.8` 进世界确认过；`1.20.1` / `1.20.4` /
  `1.20.6` / `26.3` 只验证到"模组被加载"，还没进世界看手臂。26.3 的目标类刚被重构，尤其需要实测。
- **IDE 的 Gradle 同步失败原因未定位**：CLI 侧各分支全部正常，无法复现 IDE 的失败。需要具体的 IDE 报错文本才能继续。
- **26.1.0 / 26.1.1 的方法名未核对**：26.1.2 是 `renderArmWithItem`，同线更早版本没验证过。
- **渲染线程每帧读配置**：`SimpleOffhandConfig` 的两个方法每帧各调一次 `ConfigValue#get()`。暂不处理；真要做就监听 `ModConfigEvent.Loading` 把值读进字段，顺带消掉"配置未加载即读取"会抛 `IllegalStateException` 的隐患。
- **注入是否真的生效只能进游戏看**：方法名或参数类型写错**不会编译报错**，运行期才抛 `Mixin apply failed`。进游戏、空着副手看第一人称，日志里出现 `[SimpleOffhand/]: Offhand arm rendering active (injection applied).` 即为生效。
