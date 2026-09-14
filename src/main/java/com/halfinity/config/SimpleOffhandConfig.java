package com.halfinity.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * 模组配置。
 *
 * <p>配置项只在 {@link CommonConfig} 里声明，对外一律通过本类的静态方法读取，
 * 不把 {@link ModConfigSpec.ConfigValue} 直接抛给调用方。配置界面（{@code ConfigurationScreen}）
 * 显示的标题和悬浮提示取自翻译键 {@code simpleoffhand.config.*}，也就是
 * {@code assets/simpleoffhand/lang/} 下的语言文件；而配置文件里的 {@code #} 注释来自
 * {@link ModConfigSpec.Builder#comment}。</p>
 */
public final class SimpleOffhandConfig {

    /** 默认的“双手物品”。主手拿地图时原版会双手举图，所以默认跟着原版走。 */
    private static final List<String> DEFAULT_TWO_HANDED_ITEMS = List.of("minecraft:filled_map");

    private SimpleOffhandConfig() {
    }

    /** 配置项声明。 */
    public static final class CommonConfig {

        /** 模组总开关。 */
        public final ModConfigSpec.ConfigValue<Boolean> modEnabled;

        /** “双手物品”的物品 ID 列表。 */
        public final ModConfigSpec.ConfigValue<List<? extends String>> twoHandedItems;

        CommonConfig(ModConfigSpec.Builder builder) {
            modEnabled = builder
                    .comment("Enable the mod. Set to false to render the first-person hands exactly like vanilla.")
                    .translation("simpleoffhand.config.modEnabled")
                    .define("modEnabled", true);

            twoHandedItems = builder
                    .comment("Item IDs that keep the vanilla offhand behaviour while held in the main hand.",
                            "When one of these is held in the main hand and the offhand is empty, no extra",
                            "offhand arm is drawn. This is what vanilla already does for a two-handed map.",
                            "Format: \"namespace:path\", e.g. \"minecraft:filled_map\".")
                    .translation("simpleoffhand.config.twoHandedItems")
                    .defineList(
                            "twoHandedItems",
                            DEFAULT_TWO_HANDED_ITEMS,
                            () -> "minecraft:filled_map",
                            SimpleOffhandConfig::isValidItemId
                    );
        }
    }

    /** 配置实例。 */
    public static final CommonConfig COMMON;

    /** 配置规格，注册到 NeoForge 用。 */
    public static final ModConfigSpec COMMON_SPEC;

    static {
        Pair<CommonConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(CommonConfig::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    /** 模组是否启用。 */
    public static boolean isEnabled() {
        return COMMON.modEnabled.get();
    }

    /**
     * 判断某个物品是否属于配置里的“双手物品”。
     *
     * <p>按注册名（如 {@code minecraft:filled_map}）比较，不去注册表里反查物品实例，
     * 这样配置里写错的 ID 只会“匹配不上”，不会在渲染线程里抛异常。</p>
     *
     * @param stack 待判断的物品，允许传入空栈
     * @return 该物品在配置列表中返回 true
     */
    public static boolean isTwoHandedItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && COMMON.twoHandedItems.get().contains(id.toString());
    }

    /** 校验配置里的物品 ID 是否合法，供 {@code defineList} 使用。 */
    private static boolean isValidItemId(Object entry) {
        return entry instanceof String id && Identifier.tryParse(id) != null;
    }
}
