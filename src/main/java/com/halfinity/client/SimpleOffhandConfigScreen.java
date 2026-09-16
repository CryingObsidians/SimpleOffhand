package com.halfinity.client;

import com.halfinity.config.SimpleOffhandConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.ConfigScreenHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * SimpleOffhand 鐨勯厤缃晫闈€? *
 * <p>NeoForge 20.4 鏈?ConfigScreenHandler.ConfigScreenFactory 鎵╁睍鐐癸紝浣嗕笉浼氳嚜鍔ㄧ敓鎴? * 閰嶇疆鐣岄潰锛欳onfigScreenHandler.getScreenFactoryFor 鍙細鍘诲彇妯＄粍鑷繁娉ㄥ唽鐨勯偅涓墿灞曠偣
 * 锛圡odContainer.getCustomExtension锛夛紝娌℃敞鍐屽氨娌℃湁銆岄厤缃€嶆寜閽€傛墍浠ヨ繖閲岃嚜鍐欎竴涓€?/p>
 *
 * <p>鍙敤鏈€鍩虹鐨?Screen API锛岄伩鍏嶄緷璧栧悇鐗堟湰涔嬮棿浼氬彉鍔ㄧ殑 widget 宸ュ叿绫汇€?/p>
 */
public class SimpleOffhandConfigScreen extends Screen {

    private final Screen parent;
    private boolean enabled;
    private EditBox twoHandedItemsBox;
    private Button toggleButton;

    /** ConfigScreenHandler.ConfigScreenFactory 瑕佹眰鐨勬瀯閫犲櫒绛惧悕銆?*/
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

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save())
                .bounds(centerX - 100, y + 80, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(centerX + 5, y + 80, 95, 20).build());
    }

    private Component toggleLabel() {
        return Component.translatable("simpleoffhand.config.modEnabled")
                .append(": ")
                .append(Component.translatable(this.enabled ? "options.on" : "options.off"));
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