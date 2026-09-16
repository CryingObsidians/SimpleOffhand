package com.halfinity;

import com.halfinity.client.SimpleOffhandConfigScreen;
import com.halfinity.config.SimpleOffhandConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.ConfigScreenHandler;

/**
 * 模组主类，只做两件事：注册配置文件、注册配置界面。
 *
 * <p>本模组改的全是第一人称手部的渲染，用 {@link OnlyIn}{@code (Dist.CLIENT)} 限定客户端。</p>
 *
 * <p>配置用 {@link ModConfig.Type#CLIENT}，配置文件名为
 * {@code config/simpleoffhand-client.toml}。</p>
 *
 * <p>这条分支的 FML 是 2.0.17（NeoForge 20.4），比 1.21.x 用的 10.x 早很多，三处不同：</p>
 * <ul>
 *   <li>{@code @Mod} 只有 {@code value} 一个属性，没有 {@code dist}，所以用 {@link OnlyIn}；</li>
 *   <li>{@link ModContainer} 上没有 {@code registerConfig}，配置要经
 *       {@link ModLoadingContext#registerConfig} 注册；</li>
 *   <li>配置界面必须显式注册 {@link ConfigScreenHandler.ConfigScreenFactory}，
 *       NeoForge 20.4 不会自动生成界面。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod(value = Simpleoffhand.MODID)
public class Simpleoffhand {

    /** 模组 ID，必须和 gradle.properties 里的 mod_id 保持一致。 */
    public static final String MODID = "simpleoffhand";

    public Simpleoffhand(ModContainer container) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SimpleOffhandConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(SimpleOffhandConfigScreen::new));
    }
}
