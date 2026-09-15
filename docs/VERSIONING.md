# SimpleOffhand 版本差异

## 分支与坐标

| 分支 | MC | NeoForge | `neo_version_range` | JDK | Gradle |
| --- | --- | --- | --- | --- | --- |
| `master` / `26.2` | 26.2 | 26.2.0.88 | `[26.2.0,)` | 25 | 9.6.1 |
| `26.1.2` | 26.1.2 | 26.1.2.109 | `[26.1.2,)` | 25 | 9.6.1 |
| `1.21.11` | 1.21.11 | 21.11.45 | `[21.11,)` | 21 | 8.8 |
| `1.21.10` | 1.21.10 | 21.10.64 | `[21.10,)` | 21 | 8.8 |
| `1.21.8` | 1.21.8 | 21.8.54 | `[21.8,)` | 21 | 8.8 |
| `1.21.1` | 1.21.1 | 21.1.250 | `[21.1,)` | 21 | 8.8 |
| `1.20.6` | 1.20.6 | — | — | 17 | 8.8 |
| `1.20.4` | 1.20.4 | — | — | 17 | 8.8 |
| `1.20.1` | 1.20.1 | — | — | 17 | 8.8 |

`minecraft_version_range` 一律锁到具体版本（如 `[1.21.8]`）：各条线由 NeoForge 并行维护，写跨线区间会让 FML 在不兼容的版本上放行加载。

1.21.4 / 1.21.5 等不计划做。

## Mixin 类与方法

`com.halfinity.mixin.ItemInHandRendererMixin` → `net.minecraft.client.renderer.ItemInHandRenderer`

| 分支 | 注入目标 | 注入方法与 `renderPlayerArm` 的第 2 个参数 |
| --- | --- | --- |
| `master` / `26.2` | `submitArmWithItem` | `SubmitNodeCollector` |
| `26.1.2` | `renderArmWithItem` | `SubmitNodeCollector` |
| `1.21.11` | `renderArmWithItem` | `SubmitNodeCollector` |
| `1.21.10` | `renderArmWithItem` | `SubmitNodeCollector` |
| `1.21.8` | `renderArmWithItem` | `MultiBufferSource` |
| `1.21.1` | `renderArmWithItem` | `MultiBufferSource` |
| `1.20.6` / `1.20.4` / `1.20.1` | 未验证 | 未验证 |

只有 26.2 改了方法名。注入方法的参数列表各版本都是 10 个，且只有第 9 个参数的类型在两代管线之间变过（换代点在 1.21.8 与 1.21.10 之间）。

## 其它版本差异

| | |
| --- | --- |
| 标识符类 | 1.21.11 起是 `net.minecraft.resources.Identifier`；1.21.10 及更早是 `ResourceLocation`。用到两处：`BuiltInRegistries.ITEM.getKey(...)` 与 `tryParse(...)` |
| 模组图标 | 26.2 用 `iconFile` + `bannerFile`；26.1.2 及 1.21.x 只能用 `logoFile` |

## 未解决

- **1.20.6 / 1.20.4 / 1.20.1 未做**：需要 JDK 17，本机没有。
- **1.20.x 的注入目标未核对**：`renderArmWithItem` 与 `renderPlayerArm` 的签名、第 9 个参数是 `MultiBufferSource` 还是 `VertexConsumer`，都不确定。
- **26.1.0 / 26.1.1 的方法名未核对**：26.1.2 是 `renderArmWithItem`，同线更早版本没验证过。
- **渲染线程每帧读配置**：`SimpleOffhandConfig` 的两个方法每帧各调一次 `ConfigValue#get()`。暂不处理；真要做就监听 `ModConfigEvent.Loading` 把值读进字段，顺带消掉"配置未加载即读取"会抛 `IllegalStateException` 的隐患。
- **注入是否真的生效只能进游戏看**：方法名或参数类型写错**不会编译报错**，运行期才抛 `Mixin apply failed`。进游戏、空着副手看第一人称，日志里出现这行即为生效：

  ```
  [Render thread/INFO] [SimpleOffhand/]: Offhand arm rendering active (injection applied).
  ```
