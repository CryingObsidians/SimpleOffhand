package com.halfinity;

import com.halfinity.client.SimpleOffhandConfigScreen;
import com.halfinity.config.SimpleOffhandConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 模组主类，只做两件事：注册配置文件、注册配置界面。
 *
 * <p>本模组只改第一人称手部渲染，声明为 {@link Dist#CLIENT}。</p>
 *
 * <p>配置用 {@link ModConfig.Type#CLIENT}，配置文件名为
 * {@code config/simpleoffhand-client.toml}。</p>
 *
 * <p>这条分支的 NeoForge 是 20.6（FML 3.0.45），比 1.21.x 用的 10.x 早：
 * 有 {@link IConfigScreenFactory} 但**没有内置的 {@code ConfigurationScreen}**，
 * 所以要自己提供一个界面实现（{@link SimpleOffhandConfigScreen}）。</p>
 */
@Mod(value = Simpleoffhand.MODID, dist = Dist.CLIENT)
public class Simpleoffhand {

    /** 模组 ID，必须和 gradle.properties 里的 mod_id 保持一致。 */
    public static final String MODID = "simpleoffhand";

    public Simpleoffhand(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SimpleOffhandConfig.CLIENT_SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, SimpleOffhandConfigScreen::new);

        // TEMP-DEBUG 仅用于本地截图核对界面，验证完删除
        if (System.getProperty("simpleoffhand.debugUi") != null) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(new SimpleOffhandConfigScreen(mc,
                    new net.minecraft.client.gui.screens.TitleScreen())));
        }
        // TEMP-DEBUG-END
    }
}
