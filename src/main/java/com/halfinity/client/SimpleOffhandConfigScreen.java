package com.halfinity.client;

import com.halfinity.config.SimpleOffhandConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * SimpleOffhand 的配置界面。
 *
 * <p>NeoForge 20.6 有 IConfigScreenFactory，但没有内置的自动配置界面
 * （ConfigurationScreen 要到 20.5 之后才有），所以这里自写一个。</p>
 *
 * <p>只用最基础的 Screen API + CommonComponents，避免依赖各版本之间会变动的
 * widget 工具类。</p>
 */
public class SimpleOffhandConfigScreen extends Screen {

    private final Screen parent;
    private boolean enabled;
    private EditBox twoHandedItemsBox;
    private Button toggleButton;

    /** IConfigScreenFactory 要求的构造器签名。 */
    public SimpleOffhandConfigScreen(net.minecraft.client.Minecraft minecraft, Screen parent) {
        super(Component.translatable("simpleoffhand.configuration.title"));
        this.parent = parent;
        this.enabled = SimpleOffhandConfig.isEnabled();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 4 + 20;

        this.toggleButton = Button.builder(toggleLabel(), b -> {
            this.enabled = !this.enabled;
            b.setMessage(toggleLabel());
        }).bounds(centerX - 100, y, 200, 20).build();
        addRenderableWidget(this.toggleButton);

        this.twoHandedItemsBox = new EditBox(this.font, centerX - 100, y + 40, 200, 20,
                Component.translatable("simpleoffhand.config.twoHandedItems"));
        this.twoHandedItemsBox.setValue(String.join(", ", SimpleOffhandConfig.getTwoHandedItems()));
        addRenderableWidget(this.twoHandedItemsBox);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(centerX - 100, y + 80, 95, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centerX + 5, y + 80, 95, 20).build());
    }

    private Component toggleLabel() {
        return CommonComponents.optionNameValue(
                Component.translatable("simpleoffhand.config.modEnabled"),
                CommonComponents.optionStatus(this.enabled));
    }

    private void save() {
        SimpleOffhandConfig.setEnabled(this.enabled);

        List<String> items = new ArrayList<>();
        for (String part : this.twoHandedItemsBox.getValue().split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        SimpleOffhandConfig.setTwoHandedItems(items);
        SimpleOffhandConfig.save();

        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4 - 20, 0xFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("simpleoffhand.config.twoHandedItems"),
                this.width / 2 - 100, this.height / 4 + 66, 0xA0A0A0);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}