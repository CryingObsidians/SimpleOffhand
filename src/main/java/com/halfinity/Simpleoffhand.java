package com.halfinity;

import com.halfinity.config.SimpleOffhandConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/**
 * 模组主类。
 * 负责注册配置文件和配置界面。
 */
@Mod(Simpleoffhand.MODID)
public class Simpleoffhand {

    /** 模组 ID，用于标识本模组 */
    public static final String MODID = "simpleoffhand";

    public Simpleoffhand(ModContainer container) {
        // 注册通用配置文件
        container.registerConfig(ModConfig.Type.COMMON, SimpleOffhandConfig.COMMON_SPEC);

        // 注册配置界面工厂，点击模组列表中的"配置"按钮时打开自动生成的配置界面
        container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) ->
                new ConfigurationScreen(container, parent)
        );
    }
}