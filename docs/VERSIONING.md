# SimpleOffhand 多版本分支规划

目标：一份代码覆盖 Minecraft **1.20.1 → 26.2**。每个版本一条分支，`master` 跟随最新版。

## 分支结构

| 分支 | MC 版本 | 说明 |
| --- | --- | --- |
| `master` | **26.2** | 最新版，默认分支 |
| `26.2` | 26.2 | master 的快照，固定 26.2 现场 |
| `26.1.2` | 26.1.2 | 26.1 线的补丁版本 |
| `1.21.11` | 1.21.11 | 旧编号体系；Gradle 8.8 + JDK 21 |
| `1.21.10` | 1.21.10 | 旧编号体系；Gradle 8.8 + JDK 21 |

> 以后每加一个版本就多一条同名分支；`master` 始终等于最新版。

## 版本区间策略：只覆盖自己那一条线

`minecraft_version_range` **不写跨版本区间**。

26.x 的各条线（`26.1` / `26.1.1` / `26.1.2` / `26.2` …）由 NeoForge **并行维护**，
彼此的渲染管线签名并不相同。写成 `[26.1,26.2)` 会把 26.1.0 / 26.1.1 也算进兼容范围，
模组会在根本不兼容的版本上被 FML 放行加载，然后在运行期炸掉。

因此本项目锁定到具体版本：

| 分支 | `minecraft_version` | `minecraft_version_range` |
| --- | --- | --- |
| `master` / `26.2` | `26.2` | `[26.2]` |
| `26.1.2` | `26.1.2` | `[26.1.2]` |
| `1.21.11` | `1.21.11` | `[1.21.11]` |
| `1.21.10` | `1.21.10` | `[1.21.10]` |

`neo_version_range` 同理收紧到对应的 NeoForge 线
（`[26.2.0,)` / `[26.1.2,)` / `[21.11,)` / `[21.10,)`），
不要写成 `[26,)` —— 那会把 26.1 线的 NeoForge 也认作满足条件。

> 如果希望同一条线的后续补丁版本（例如将来出现 26.2.1）也能加载，
> 把范围放宽成 `[26.2,26.3)` 即可。放宽到 `[26.1,26.2)` 这种跨线的写法是错的。

`minecraft_version` 这一项在构建脚本里**只作记录**，不参与 MC 产物解析：
ModDevGradle 是从 `neo_version` 反推 MC 版本的（`VersionCapabilitiesInternal.ofNeoForgeVersion`）。
但它的值要和实际解析结果一致，否则文档会误导人。

## NeoForge 版本坐标

NeoForge 的版本号与 MC 版本对应关系**不是统一的**，需要逐条确认：

| MC 版本 | NeoForge 版本线 | 最新构建 | 备注 |
| --- | --- | --- | --- |
| 26.2 | `26.2.0.x` | `26.2.0.88` | 独立线 |
| 26.1.2 | `26.1.2.x` | `26.1.2.109` | 独立线 |
| 26.1.1 | `26.1.1.x` | `26.1.1.15-beta` | 独立线 |
| 26.1 | `26.1.0.x` | `26.1.0.19-beta` | 独立线 |
| 1.21.11 | `21.11.x` | `21.11.45` | 老编号体系，beta 到 `21.11.41-beta` |
| 1.21.10 | `21.10.x` | `21.10.64` | 老编号体系，已转正式版 |
| 1.20.1 | `20.1.x` | — | NeoForge 起点 |

老编号体系的规则：**MC 版本去掉开头的 `1.`**，就是 NeoForge 的版本线
（`1.21.11` → `21.11.x`，`1.21.10` → `21.10.x`，`1.21.4` → `21.4.x`，`1.20.1` → `20.1.x`）。
NeoForge 自己的解析正则也是这么做的（`VersionCapabilitiesInternal.neoForgeVersionToMinecraftVersion`）。

**注意**：26.1.2 的 NeoForge 版本是 `26.1.2.109`（与 MC 版本号同形），
而 26.2 是 `26.2.0.88`（第三段是 0）。不要想当然地套用。

26.2 线在 `26.2.0.56-beta` 之前都是 beta，从 `26.2.0.57` 起转为正式版。
选版本时优先取正式版，不要用 beta。

查询权威来源：
```bash
# 全部版本列表
curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml
```

## ModDevGradle 插件版本必须跟着 NeoForge 走

**这是升级 NeoForge 时最容易踩的坑。**

`build.gradle` 里的 `net.neoforged.moddev` 版本决定了 neoform 管线（含 jst 源码变换、
AccessTransformer 应用）的实现。新 NeoForge 如果新增了旧插件处理不了的 AT 或变换，
构建会在 `createMinecraftArtifacts` 的 **recompile** 阶段失败。

实测案例：NeoForge `26.2.0.88` 新增了两条指向**匿名内部类**的 AT：

```
public net.minecraft.core.HolderSet$Named contents()Ljava/util/List;
public net.minecraft.core.HolderSet$1 contents()Ljava/util/List;
```

`moddev` `2.0.143` 无法把 AT 应用到 `HolderSet$1` 这类匿名类名上，
于是 `contents()` 保持 package-private，javac 报：

```
ERROR Line: 44, <HolderSet$1> contents() ... HolderSet$Named contents()
      ... must be public in /net/minecraft/core/HolderSet.java
Caused by: java.io.IOException: Compilation failed
> Task :createMinecraftArtifacts FAILED
```

**升级到 `2.0.147` 即可解决。** 注意这个报错完全发生在 NeoForge 自己的补丁管线里，
和你的代码无关 —— 别去改自己的 mixin，改插件版本。

排查方法（对比两个 NeoForge 版本的 AT/注入接口差异）：

```python
import os, zipfile, difflib
base = os.path.expanduser('~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge')
def read(v, member):
    for h in os.listdir(os.path.join(base, v)):
        for f in os.listdir(os.path.join(base, v, h)):
            if f.endswith('-universal.jar'):
                return zipfile.ZipFile(os.path.join(base, v, h, f)).read(member).decode()
a, b = '26.2.0.45-beta', '26.2.0.88'
print('\n'.join(difflib.unified_diff(
    read(a, 'META-INF/accesstransformer.cfg').splitlines(),
    read(b, 'META-INF/accesstransformer.cfg').splitlines(), a, b, lineterm='', n=1)))
```

同理可对比 `META-INF/injected-interfaces.json`，以及 `-userdev.jar` 里的
`patches/**/*.patch`（MC 补丁本体）。

## 各版本技术差异（关键）

### 26.1.2 → 26.2

**差异一：目标方法改名。**

| | 26.1.2 | 26.2 |
| --- | --- | --- |
| 方法名 | `renderArmWithItem` | `submitArmWithItem` |
| `renderPlayerArm` | `(PoseStack, SubmitNodeCollector, int, float, float, HumanoidArm)` | 同左 |
| 渲染管线 | `SubmitNodeCollector` | `SubmitNodeCollector`（同） |

参数列表完全一致，只是名字变了。`@Inject(method = "...")` 里的字符串要跟着改。

> ⚠️ **方法名写错不会编译报错**，Mixin 在运行期才抛 `Mixin apply failed`。
> 换版本后必须进游戏验证，不能只看编译通过。

**差异二：模组元数据的图标字段改名。**

新 NeoForge 用 `bannerFile` + `iconFile` 取代了旧的 `logoFile`。
`logoFile` 已弃用，用它会触发警告：

```
Mod <id> uses the deprecated `logoFile` property; change to `bannerFile` and/or (for square icons) `iconFile`
```

| | `neoforge.mods.toml` 里写 | 图片文件 | 图片内容 |
| --- | --- | --- | --- |
| 1.21.10（NeoForge 21.10.64） | `logoFile = "simpleoffhand.png"` | 只有一张正方形图 | 正方形 |
| 1.21.11（NeoForge 21.11.45） | `logoFile = "simpleoffhand.png"` | 只有一张正方形图 | 正方形 |
| 26.1.2（NeoForge 26.1.2.109） | `logoFile = "simpleoffhand.png"` | 只有一张正方形图 | 正方形 |
| 26.2（NeoForge 26.2.0.88+） | `iconFile = "simpleoffhand.png"`<br>`bannerFile = "simpleoffhand-banner.png"` | `simpleoffhand.png` + `simpleoffhand-banner.png` | 正方形（512×512）+ 宽幅横幅 |

两者的区别：`iconFile` 是**正方形图标**，`bannerFile` 是**宽幅横幅**（模组列表顶部的图）。

26.2 分支**两个字段都要写**，各指一张图；少写 `bannerFile` 模组列表顶部就没有横幅。
26.1.2 与两条 1.21.x 分支没有横幅文件，也不能写这两个字段，只用 `logoFile`。

> ⚠️ 这里有个容易误判的地方：**`iconFile` / `bannerFile` 不是 FML 解析的字段**。
> FML 的 `net.neoforged.fml.loading.moddiscovery.ModInfo` 只认 `logoFile` / `logoBlur`
> （旧版兼容），所以去 loader jar 里搜 `iconFile` 是搜不到的 —— 别据此判断"没这个字段"。
> `iconFile` / `bannerFile` / `iconBlur` 是 **NeoForge 自己**在
> `net.neoforged.neoforge.client.gui.modlist.DefaultModDisplayInfo` 里直接读 toml 取的。
>
> 判断某个 NeoForge 版本是否支持，看它有没有这两个类：
> - `net/neoforged/neoforge/client/gui/modlist/DefaultModDisplayInfo`
> - `net/neoforged/neoforge/internal/LogoFileWarningsHandler`
>
> 26.1.2.109 **两个类都不存在**，所以 26.1.2 分支必须继续用 `logoFile`，
> 写成 `iconFile` 会被忽略、图标不显示。`21.10.64` / `21.11.45` 同理，也只能用 `logoFile`。


### 26.x 全线：Java 25（已实测）

**26.1.2 与 26.2 一样要求 Java 25**，`compatibilityLevel` 用 `JAVA_25`。

原因：NeoForge 26.x 依赖的 FancyModLoader 11.x 只有 Java 25 变体：

```
net.neoforged.fancymodloader:loader:11.0.15
  → only compatible with JVM runtime version 25 or newer
```

把 `java.toolchain.languageVersion` 设成 21 会报 `No matching variant of
net.neoforged:neoforge:26.1.2.109 was found`（一长串变体不匹配），
根因就写在最后几条里：`fancymodloader` / `earlydisplay` / `neoform` 都只声明了 Java 25。
**26.x 全线统一用 JDK 25，不要按 MC 小版本去猜 Java 版本。**

### 1.21.10 / 1.21.11：管线已经是新的，只有方法名不同（已实测）

**本节原本写的是「1.21 及更早用的是旧的立即模式渲染，没有 `SubmitNodeCollector`」，这是错的**，
已被实测推翻。NeoForge `21.11.45` + MC 1.21.11 生成出来的源码是：

```java
// net/minecraft/client/renderer/ItemInHandRenderer
private void renderArmWithItem(
        AbstractClientPlayer player, float partialTick, float pitch,
        InteractionHand hand, float swingProgress, ItemStack itemStack,
        float equippedProgress, PoseStack poseStack,
        SubmitNodeCollector collector, int lightCoords)

private void renderPlayerArm(
        PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
        float equippedProgress, float swingProgress, HumanoidArm arm)
```

- **已经有 `SubmitNodeCollector`**，也在用 `AvatarRenderer`（`renderRightHand` / `renderLeftHand`）
- `renderPlayerArm` 的签名**与 26.x 一字不差**
- **唯一区别**：被注入的方法在 1.21.11 叫 **`renderArmWithItem`**，26.2 起改名成 `submitArmWithItem`

所以各分支之间代码差异**只有 `@Inject(method = ...)` 里那个字符串**。

| | 1.21.10 / 1.21.11 | 26.2 |
| --- | --- | --- |
| 注入目标方法 | `renderArmWithItem` | `submitArmWithItem` |
| 参数列表 | 相同（10 个） | 相同（10 个） |
| `renderPlayerArm` | `(PoseStack, SubmitNodeCollector, int, float, float, HumanoidArm)` | 同左 |

> ⚠️ **1.21.10 与 1.21.11 之间也有差异，别以为相邻小版本就能照抄。**
> 1.21.10 分支是从 1.21.11 直接派生的，第一次构建时唯一的编译错误就是标识符类改名：
>
> ```
> 错误: 找不到符号
> import net.minecraft.resources.Identifier;
>                                ^
>   符号:   类 Identifier
> ```
>
> 1.21.10 用的还是 **`ResourceLocation`**，`Identifier` 是 1.21.11 才改的名。
> `SimpleOffhandConfig` 里有两处要用到：取注册名的
> `BuiltInRegistries.ITEM.getKey(...)` 和校验用的 `tryParse(...)`，
> 两个类都提供同名方法，所以只是类型名替换。
>
> 除了这个类名和 `gradle.properties` 里的版本坐标，1.21.10 与 1.21.11
> **没有别的差异** —— 渲染管线、注入签名、原版空手分支的逻辑都完全相同（已实测）。

### 1.21.10 / 1.21.11 与 26.x 的工具链差异

| | 1.21.10 / 1.21.11 | 26.x |
| --- | --- | --- |
| JDK | **21** | 25 |
| Gradle | **8.8** | 9.6.1 |
| `compatibilityLevel` | `JAVA_21` | `JAVA_25` |
| FML loader | `10.x`（`21.10.64` → `10.0.32`，`21.11.45` → `10.0.36`） | `11.x` |
| 图标字段 | `logoFile` | 26.2 = `iconFile` + `bannerFile` |

26.x 要 Java 25 是因为 FancyModLoader 11.x 只声明了 Java 25 变体；
1.21.x 用 loader 10.x，没这个限制，所以是 Java 21。

### ⚠️ Gradle 8.8 跑不了 JDK 25

Gradle 8.8 官方只支持到 Java 22。若守护进程跑在 JDK 25 上，构建直接失败：

```
BUG! exception in phase 'semantic analysis' in source unit '_BuildScript_'
Unsupported class file major version 69
```

所以 `1.21.10` / `1.21.11` 分支必须**同时**把守护进程的 JVM 钉到 21，用 Gradle 8.8 自带的
daemon JVM 特性，文件 `gradle/gradle-daemon-jvm.properties`：

```
#This file is generated by updateDaemonJvm
toolchainVersion=21
```

（本该由 `./gradlew updateDaemonJvm --jvm-version=21` 生成，但那个任务自身也要先能跑起来 ——
守护进程还在 JDK 25 上时它同样报 major version 69，所以这份文件是手写的。）

生效时日志里会出现 `Daemon JVM discovery is an incubating feature.`。
注意 `gradlew --version` 显示的仍是启动它的那个 JDK（`JVM: 25.0.3`），
**那只是启动器的 JVM，不代表构建守护进程用的 JVM**，别被它误导。

### 更早的版本（1.20.1 / 1.21.4 等，待实测）

到目前为止实测过的是 **1.21.10 / 1.21.11**（以及 26.1.2、26.2）。
更早的 1.20.x、早期 1.21.x 是不是也已经有 `SubmitNodeCollector`、
`renderPlayerArm` 的签名是否相同，**都不确定**，适配前必须重新核对
（Java 版本也不一样：1.20.1 → 17）。不要照抄 26.x 或 1.21.x 的实现。

## 换版本时的检查清单

改一条新版本分支时，按顺序确认：

1. **`gradle.properties`**
   - `minecraft_version` / `minecraft_version_range`（锁到具体版本，见上文策略）
   - `neo_version` / `neo_version_range`（去 Maven metadata 查，别猜）
   - `mod_version`（形如 `<mc版本>+<mod版本>`，如 `26.2+0.1.0`）
2. **`build.gradle`**
   - `java.toolchain.languageVersion`（26.x = `25`，1.21.x = `21`，1.20.1 = `17`）
   - `net.neoforged.moddev` 插件版本（新 NeoForge 通常要配新插件，见上文）
3. **`simpleoffhand.mixins.json`**：`compatibilityLevel`（26.x = `JAVA_25`，1.21.x = `JAVA_21`）
4. **Gradle 版本**：`gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl`
   （26.x = 9.6.1，1.21.x = 8.8），以及 `gradle/gradle-daemon-jvm.properties`
   里守护进程的 `toolchainVersion` —— **Gradle 版本和守护进程 JVM 必须匹配得上**，
   旧 Gradle 跑在新 JDK 上会报 `Unsupported class file major version`。
5. **`src/main/templates/META-INF/neoforge.mods.toml`**：图标字段
   （26.2 = `iconFile` + `bannerFile`；其余分支 = `logoFile`，见上文）
6. **Mixin 方法名与签名**：从 javadoc / 本地 sources jar 核对
7. **全仓搜一遍改名的 API**：`ResourceLocation` ↔ `Identifier` 这类改名不会出现在
   javadoc 的"重大变更"里，只能靠编译报错暴露（1.21.10 就是这么踩到的）
8. **`README.md`**：环境要求表、`./gradlew` 说明里的 JDK 版本、多版本分支表
9. **构建验证**：`clean build --no-build-cache`，确认无 `FROM-CACHE`
10. **运行验证**：`./gradlew runClient` 进游戏看行为（编译过 ≠ Mixin 生效）

> 第 9 步除了看有没有 `FROM-CACHE`，还要确认 `build/libs/` 里只有**本分支**那一个 jar。
> 切过分支之后留下的旧 jar（在 1.21.10 上看到 `simpleoffhand-26.2+*.jar` 这种）
> 会被误当成产物发布，构建前先 `clean` 一次。

## 配置类型：用 CLIENT，不用 COMMON

本模组的配置注册成 `ModConfig.Type.CLIENT`（`Simpleoffhand.java`）：

```java
container.registerConfig(ModConfig.Type.CLIENT, SimpleOffhandConfig.CLIENT_SPEC);
```

理由：这是纯客户端模组（`@Mod(dist = Dist.CLIENT)`），配置项只影响第一人称渲染，
没有任何一项需要服务器知道。`CLIENT` 的语义正好对应（FML 源码里的注释）：

| | `COMMON` | `CLIENT` |
| --- | --- | --- |
| 加载端 | 客户端和服务器都加载 | **只在客户端加载** |
| 存放位置 | 全局 `config/` | 全局 `config/`（同） |
| 是否同步 | 不同步 | 不同步（同） |
| 默认文件名 | `<modid>-common.toml` | **`<modid>-client.toml`** |

注意最后一行：**改类型会改文件名**（`extension()` 就是类型名小写）。
本项目因此是 `config/simpleoffhand-client.toml`，旧版本留下的
`simpleoffhand-common.toml` 不会被读取，改名或删掉即可。

顺带一个容易担心的点：`ConfigValue#get()` 在配置尚未加载时会抛 `IllegalStateException`，
所以"配置没加载完就被渲染代码读到"是危险的。这里不会发生 —— NeoForge 在
`CommonModLoader.begin()` 里加载配置，顺序是「Registry initialization」→「Config loading」
→`NeoEventBus.start()`，而渲染远在其后：

```java
// net/neoforged/neoforge/internal/CommonModLoader.java
if (!datagen) {
    ModLoader.runInitTask("Config loading", syncExecutor, periodicTask, () -> {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.CLIENT, FMLPaths.CONFIGDIR.get());
        }
        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FMLPaths.CONFIGDIR.get());
    });
}
```

`Type.CLIENT` 在 FML 10.x（1.21.11 用的那代）和 11.x（26.x）里都存在，两条线都能这么写。

## 已知待办

### 渲染线程每帧读配置

`SimpleOffhandConfig.isEnabled()` 和 `isTwoHandedItem()` 每帧各读一次
`ModConfigSpec.ConfigValue#get()`。列表通常只有一项，开销可以忽略，所以**暂时不动**。

真要优化的话，不要在外面加缓存字段，正道是监听 `ModConfigEvent.Loading`，
加载/重载时把值读进普通字段，渲染路径只读字段。这样能一次消掉两个问题：
每帧读配置，以及"配置未加载就被读到"（`ConfigValue#get()` 会抛 `IllegalStateException`）。

### 渲染行为的改动只能靠进游戏验证

`@Inject` 里的方法名写错**不会编译报错**，Mixin 在运行期才抛 `Mixin apply failed`。
所以每次换版本、改 `@Inject` 之后都必须 `./gradlew runClient` 进游戏看一眼，
编译通过不等于 Mixin 生效。

## 有用的 javadoc 来源

- 26.2.x: https://aldak0.ru/javadoc/26.2.x/
- 26.1.x: https://aldak0.ru/javadoc/26.1.x/
- NeoForge 26.1 events: https://lexxie.dev/neoforge/26.1/

更可靠的是**本地反编译源码**（构建后自动生成）：
```
<项目>/build/moddev/artifacts/minecraft-patched-<neoforge版本>-sources.jar
```

注意文件名用的是 **NeoForge 版本号**（如 `minecraft-patched-26.2.0.88-sources.jar`），
不是 MC 版本号。
