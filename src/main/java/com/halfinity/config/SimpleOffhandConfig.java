package com.halfinity.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * 模组配置。
 *
 * <p>配置项只在 {@link ClientConfig} 里声明，对外一律通过本类的静态方法读取，
 * 不把 {@link ModConfigSpec.ConfigValue} 直接抛给调用方。配置界面（{@code ConfigurationScreen}）
 * 显示的标题和悬浮提示取自翻译键 {@code simpleoffhand.config.*}，也就是
 * {@code assets/simpleoffhand/lang/} 下的语言文件；而配置文件里的 {@code #} 注释来自
 * {@link ModConfigSpec.Builder#comment}。</p>
 *
 * <p>这里用 {@link ModConfig.Type#CLIENT} 而不是 {@code COMMON}：本模组是纯客户端的，
 * 配置项只影响第一人称渲染，没有任何一项需要服务器读取。CLIENT 配置仅在客户端加载，
 * 文件名是 {@code simpleoffhand-client.toml}。</p>
 */
public final class SimpleOffhandConfig {

    /** 默认的“双手物品”。主手拿地图时原版会双手举图，所以默认跟着原版走。 */
    private static final List<String> DEFAULT_TWO_HANDED_ITEMS = List.of("minecraft:filled_map");

    private SimpleOffhandConfig() {
    }

    /** 配置项声明。 */
    public static final class ClientConfig {

        /** 模组总开关。 */
        public final ModConfigSpec.ConfigValue<Boolean> modEnabled;

        /** “双手物品”的物品 ID 列表。 */
        public final ModConfigSpec.ConfigValue<List<? extends String>> twoHandedItems;

        ClientConfig(ModConfigSpec.Builder builder) {
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
                            SimpleOffhandConfig::isValidItemId
                    );
        }
    }

    /** 配置实例。 */
    public static final ClientConfig CLIENT;

    /** 配置规格，注册到 NeoForge 用（注册为 {@link ModConfig.Type#CLIENT}）。 */
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        Pair<ClientConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT = pair.getLeft();
        CLIENT_SPEC = pair.getRight();
    }

    /** 模组是否启用。 */
    public static boolean isEnabled() {
        return CLIENT.modEnabled.get();
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
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && CLIENT.twoHandedItems.get().contains(id.toString());
    }

    /** 校验配置里的物品 ID 是否合法，供 {@code defineList} 使用。 */
    private static boolean isValidItemId(Object entry) {
        return entry instanceof String id && ResourceLocation.tryParse(id) != null;
    }

    // ---- 下面是给配置界面用的访问器。界面自己控制读写时机，这里只做转发。 ----

    /** 某个配置项的翻译键，供配置界面取显示名。 */
    public static String optionKey(String name) {
        return "simpleoffhand.config." + name;
    }

    /** 写入开关（界面用）。 */
    public static void setEnabled(boolean value) {
        CLIENT.modEnabled.set(value);
    }

    /** 读双手物品列表的副本。 */
    public static List<String> getTwoHandedItems() {
        return List.copyOf(CLIENT.twoHandedItems.get());
    }

    /** 写双手物品列表（界面用）。 */
    public static void setTwoHandedItems(List<String> items) {
        CLIENT.twoHandedItems.set(items);
    }

    /** 把改动落盘。 */
    public static void save() {
        CLIENT.modEnabled.save();
        CLIENT.twoHandedItems.save();
    }
}
