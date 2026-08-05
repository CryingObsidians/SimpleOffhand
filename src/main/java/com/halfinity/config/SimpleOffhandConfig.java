package com.halfinity.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 模组配置文件。
 * 定义所有可配置项，并提供读取配置的静态方法。
 */
public class SimpleOffhandConfig {

    /**
     * 配置项定义类。
     * 所有配置项在此声明并初始化。
     */
    public static class CommonConfig {
        /** 模组总开关，true 表示启用，false 表示禁用 */
        public final ModConfigSpec.ConfigValue<Boolean> modEnabled;

        /** 物品列表，存储为逗号分隔的字符串，如 "minecraft:filled_map,minecraft:shield" */
        public final ModConfigSpec.ConfigValue<String> twoHandItemsRaw;

        CommonConfig(ModConfigSpec.Builder builder) {
            // 定义总开关配置项，默认值为 true
            builder.translation("simpleoffhand.config.modEnabled");
            modEnabled = builder
                    .comment("simpleoffhand.config.modEnabled.tooltip")
                    .define("modEnabled", true);

            // 定义物品列表配置项，默认值为 "minecraft:filled_map"
            builder.translation("simpleoffhand.config.twoHandedItems");
            twoHandItemsRaw = builder
                    .comment("simpleoffhand.config.twoHandedItems.tooltip")
                    .define("twoHandItems", "minecraft:filled_map");
        }
    }

    /** 配置实例，供外部读取配置值 */
    public static final CommonConfig COMMON;

    /** 配置规格，用于注册配置到 NeoForge */
    public static final ModConfigSpec COMMON_SPEC;

    static {
        Pair<CommonConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(CommonConfig::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    /**
     * 获取模组总开关状态。
     * @return true 表示模组启用，false 表示禁用
     */
    public static boolean isModEnabled() {
        return COMMON.modEnabled.get();
    }

    /**
     * 获取物品 ID 列表。
     * 将配置中存储的逗号分隔字符串解析为字符串列表返回。
     * @return 物品 ID 列表，如 ["minecraft:filled_map", "minecraft:shield"]
     */
    public static List<String> getTwoHandItemList() {
        String raw = COMMON.twoHandItemsRaw.get().trim();
        if (raw.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }
}