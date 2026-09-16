# SimpleOffhand 版本差异

## 分支与坐标

| 分支 | MC | 平台版本 | `*_version_range` | JDK | Gradle |
| --- | --- | --- | --- | --- | --- |
| `master` / `26.2` | 26.2 | NeoForge 26.2.0.88 | `[26.2.0,)` | 25 | 9.6.1 |
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
| `1.20.6` | `renderArmWithItem` | `MultiBufferSource` |
| `1.20.4` | `renderArmWithItem` | `MultiBufferSource` |
| `1.20.1` | `renderArmWithItem` | `MultiBufferSource` |

全部签名已用 `javap` 核对各分支 `build/moddev/artifacts/` 里的制品确认。

## 平台 API 差异

| 分支 | 主类注解 | 配置 spec | 配置界面 |
| --- | --- | --- | --- |
| 26.x / 1.21.x | `@Mod(dist = Dist.CLIENT)` | `neoforge.common.ModConfigSpec` | `ConfigurationScreen`（内置，手动注册 `IConfigScreenFactory`） |
| `1.20.6` | `@Mod(dist = Dist.CLIENT)` | `neoforge.common.ModConfigSpec` | 无内置，自己实现 `IConfigScreenFactory.createScreen` |
| `1.20.4` | `@Mod(dist = Dist.CLIENT)` | `neoforge.common.ModConfigSpec` | `IConfigScreenFactory` 不存在，交给 `ConfigScreenHandler` |
| `1.20.1` | `@Mod` 无 `dist`，用 `@OnlyIn` | `minecraftforge.common.ForgeConfigSpec` | 交给 `ConfigScreenHandler`，无需注册 |

其它：

| | |
| --- | --- |
| `ModContainer.registerConfig` | 1.21.x 有；1.20.6 有；1.20.4 **没有**（走 `ModLoadingContext.get().registerConfig`） |
| `defineList` | 1.21.x 是 4 参（带"新元素默认值"）；26.x / 1.20.x 是 3 参 |
| 标识符类 | 1.21.11 起是 `Identifier`；1.21.10 及更早是 `ResourceLocation` |
| 模组图标 | 26.2 用 `iconFile` + `bannerFile`；其余分支只能用 `logoFile` |
| 编译编码 | 中文注释必须显式 `options.encoding = 'UTF-8'`，否则 Windows 上 javac 用 GBK 读取会报不可映射字符 |

## 未解决

- **1.20.x 三项都只到"构建通过"**：`1.20.1` / `1.20.4` / `1.20.6` 都还没进游戏验证。
- **1.20.4 / 1.20.6 的配置界面是自写的**：1.20.4 干脆不注册界面（依赖 `ConfigScreenHandler`），1.20.6 自写了 `client/SimpleOffhandConfigScreen`。两者的实际外观与可用性都没验证过。
- **26.1.0 / 26.1.1 的方法名未核对**：26.1.2 是 `renderArmWithItem`，同线更早版本没验证过。
- **渲染线程每帧读配置**：`SimpleOffhandConfig` 的两个方法每帧各调一次 `ConfigValue#get()`。暂不处理；真要做就监听 `ModConfigEvent.Loading` 把值读进字段，顺带消掉"配置未加载即读取"会抛 `IllegalStateException` 的隐患。
- **注入是否真的生效只能进游戏看**：方法名或参数类型写错**不会编译报错**，运行期才抛 `Mixin apply failed`。进游戏、空着副手看第一人称，日志里出现 `[SimpleOffhand/]: Offhand arm rendering active (injection applied).` 即为生效。
