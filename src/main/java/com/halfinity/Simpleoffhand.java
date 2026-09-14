package com.halfinity;

import com.halfinity.config.SimpleOffhandConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 模组主类，只做两件事：注册配置文件、注册自动生成的配置界面。
 *
 * <p>本模组改的全是第一人称手部的渲染，因此声明为 {@link Dist#CLIENT}。
 * 这样即使被放进专用服务器，NeoForge 也不会加载它，<br>
 * 也就不会去碰 {@link IConfigScreenFactory}、{@link ConfigurationScreen} 这些只在客户端存在的类。</p>
 */
@Mod(value = Simpleoffhand.MODID, dist = Dist.CLIENT)
public class Simpleoffhand {

    /** 模组 ID，必须和 gradle.properties 里的 mod_id 保持一致。 */
    public static final String MODID = "simpleoffhand";

    public Simpleoffhand(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SimpleOffhandConfig.COMMON_SPEC);

        // 让模组列表里的“配置”按钮打开自动生成的配置界面
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
