# SimpleOffhand 多版本分支规划

目标：一份代码覆盖 Minecraft **1.20.1 → 26.2**。每个版本一条分支，`master` 跟随最新版。

## 分支结构

| 分支 | MC 版本 | 说明 |
| --- | --- | --- |
| `master` | **26.2** | 最新版，默认分支 |
| `26.1.2` | 26.1.2 | 本次新增 |
| `26.2` | 26.2 | master 的快照，固定 26.2 现场 |

> 以后每加一个版本就多一条同名分支；`master` 始终等于最新版。

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

查询权威来源：
```bash
# 全部版本列表
curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml
```

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
   - `minecraft_version` / `minecraft_version_range`
   - `neo_version` / `neo_version_range`（去 Maven metadata 查，别猜）
   - `mod_version`（形如 `<mc版本>+<mod版本>`，如 `26.1.2+0.1.0`）
2. **`build.gradle`**：`java.toolchain.languageVersion`（26.x 一律 `25`；1.20.1=`17`）
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
<项目>/build/moddev/artifacts/minecraft-patched-<mc版本>-sources.jar
```
