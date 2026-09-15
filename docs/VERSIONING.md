# SimpleOffhand 多版本分支规划

目标：一份代码覆盖 Minecraft **1.20.1 → 26.2**。每个版本一条分支，`master` 跟随最新版。

## 分支结构

| 分支 | MC 版本 | 说明 |
| --- | --- | --- |
| `master` | **26.2** | 最新版，默认分支 |
| `26.2` | 26.2 | master 的快照，固定 26.2 现场 |
| `26.1.2` | 26.1.2 | 26.1 线的补丁版本 |

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

`neo_version_range` 同理收紧到对应的 NeoForge 线（`[26.2.0,)` / `[26.1.2,)`），
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
| 1.21.x | `21.x.y` | — | 老编号体系 |
| 1.20.1 | `20.1.x` | — | NeoForge 起点 |

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

**唯一差异：目标方法改名。**

| | 26.1.2 | 26.2 |
| --- | --- | --- |
| 方法名 | `renderArmWithItem` | `submitArmWithItem` |
| `renderPlayerArm` | `(PoseStack, SubmitNodeCollector, int, float, float, HumanoidArm)` | 同左 |
| 渲染管线 | `SubmitNodeCollector` | `SubmitNodeCollector`（同） |

参数列表完全一致，只是名字变了。`@Inject(method = "...")` 里的字符串要跟着改。

> ⚠️ **方法名写错不会编译报错**，Mixin 在运行期才抛 `Mixin apply failed`。
> 换版本后必须进游戏验证，不能只看编译通过。

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

### 26.x 之前的版本（待实测）

1.21 及更早用的是**旧的立即模式渲染**，没有 `SubmitNodeCollector`：
- 方法名是 `renderArmWithItem`，但签名不同（直接用 `MultiBufferSource` / `VertexConsumer`）
- 没有 `submit*` 系列
- `renderPlayerArm` 签名不同
- Java 版本要求不同（1.20.1 → Java 17，1.21.x → Java 21）

适配这些版本时**不能照抄 26.x 的实现**，要重新核对签名。

## 换版本时的检查清单

改一条新版本分支时，按顺序确认：

1. **`gradle.properties`**
   - `minecraft_version` / `minecraft_version_range`（锁到具体版本，见上文策略）
   - `neo_version` / `neo_version_range`（去 Maven metadata 查，别猜）
   - `mod_version`（形如 `<mc版本>+<mod版本>`，如 `26.2+0.1.0`）
2. **`build.gradle`**
   - `java.toolchain.languageVersion`（26.x 一律 `25`；1.20.1=`17`）
   - `net.neoforged.moddev` 插件版本（新 NeoForge 通常要配新插件，见上文）
3. **`simpleoffhand.mixins.json`**：`compatibilityLevel`（跟 Java 版本走，26.x = `JAVA_25`）
4. **Mixin 方法名与签名**：从 javadoc / 本地 sources jar 核对
5. **`README.md`**：环境要求表、`./gradlew` 说明里的 JDK 版本
6. **构建验证**：`clean build --no-build-cache`，确认无 `FROM-CACHE`
7. **运行验证**：`./gradlew runClient` 进游戏看行为（编译过 ≠ Mixin 生效）

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
