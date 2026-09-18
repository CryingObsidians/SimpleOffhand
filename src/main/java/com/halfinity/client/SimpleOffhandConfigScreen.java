package com.halfinity.client;

import com.halfinity.config.SimpleOffhandConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SimpleOffhand 的手写配置界面。
 *
 * <p>26.3～1.21.1 各分支用的是 NeoForge 内置的 {@code ConfigurationScreen}，不需要这个类。
 * 1.20.6 / 1.20.4 / 1.20.1 没有可用的内置配置界面（已用 {@code javap} 确认 20.6 的 jar 里
 * 根本不存在 {@code ConfigurationScreen}），所以只能自己写。</p>
 *
 * <h2>为什么不用 ObjectSelectionList</h2>
 *
 * <p>「可添加项目的列表」本来适合用原版的 {@code ObjectSelectionList}，但它的
 * {@code Entry#render} 参数个数在 1.20.1 / 1.20.4 / 1.20.6 之间改过，
 * {@code mouseScrolled} 的签名也不同（1.20.1 是 3 参，1.20.4 起是 4 参）。
 * 为了三条件共用同一份实现，这里不依赖它们：列表直接画在 {@code render} 里，
 * 翻页用「上一页 / 下一页」。于是整份代码只用到 Button / EditBox / Screen
 * 这三个各版本都稳定的类。</p>
 *
 * <p>与版本相关的差异只剩 {@code renderBackground} 的签名
 * （1.20.1 是单参，1.20.4 起是四参），已在各分支就地改好。</p>
 *
 * <h2>渲染时特意避开的两件事</h2>
 *
 * <p>一是<b>不依赖模糊后的菜单背景</b>：{@code Screen#renderBackground} 会把世界背景糊掉，
 * 文字直接压在糊掉的世界画面上会看着发虚、像蒙了一层毛玻璃。所以这里在内容区自己铺一块
 * 近乎不透明的面板（{@link #COLOR_PANEL}），文字都画在面板上。</p>
 *
 * <p>二是<b>「+」按钮始终可点</b>：之前输入框为空时按钮是灰的，点了没反应，看起来像功能坏了。
 * 现在按钮常亮，空输入时直接忽略。</p>
 */
public class SimpleOffhandConfigScreen extends Screen {

    // ---- 布局常量（整块内容在屏幕中垂直居中）----
    private static final int PANEL_WIDTH = 220;
    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 8;
    private static final int LABEL_HEIGHT = 12;
    private static final int CONTENT_HEIGHT =
            20                                  // 开关
            + 8 + LABEL_HEIGHT                  // 列表标签
            + 2 + VISIBLE_ROWS * ROW_HEIGHT     // 列表可视区
            + 8 + 20                            // 添加输入框
            + 8 + 20;                           // 完成 / 取消

    /** 内容面板底色：接近不透明的深灰，保证文字压在上面清晰可读。 */
    private static final int COLOR_PANEL = 0xF0101010;
    private static final int COLOR_TITLE = 0xFFFFFF;
    private static final int COLOR_LABEL = 0xC0C0C0;
    private static final int COLOR_ITEM = 0xFFFFFF;
    private static final int COLOR_HINT = 0x909090;

    /** Enter 键的键码（GLFW_KEY_ENTER / GLFW_KEY_KP_ENTER）。 */
    private static final int KEY_ENTER = 257;
    private static final int KEY_KP_ENTER = 335;

    private final Screen parent;

    /** 界面上的临时状态，点「完成」时才写回配置。 */
    private boolean enabled;
    private final List<String> items = new ArrayList<>();

    private EditBox addBox;
    private Button addButton;
    private Button prevButton;
    private Button nextButton;

    /** 当前页第一行在 {@link #items} 里的下标。 */
    private int scroll = 0;

    /** 每项对应的删除按钮，下标与 {@link #items} 一一对应。 */
    private final List<Button> removeButtons = new ArrayList<>();

    // 由 layout() 算出，供 render() 使用
    private int panelLeft;
    private int listTop;
    private int addBoxY;

    /** ConfigScreenHandler.ConfigScreenFactory 要求的构造器签名。 */
    public SimpleOffhandConfigScreen(net.minecraft.client.Minecraft minecraft, Screen parent) {
        super(Component.translatable("simpleoffhand.configuration.title"));
        this.parent = parent;
        this.enabled = SimpleOffhandConfig.isEnabled();
        this.items.addAll(SimpleOffhandConfig.getTwoHandedItems());
    }

    @Override
    protected void init() {
        layout();

        addRenderableWidget(Button.builder(toggleLabel(), b -> {
            this.enabled = !this.enabled;
            b.setMessage(toggleLabel());
        }).bounds(this.panelLeft, contentTop(), PANEL_WIDTH, 20).build());

        // 输入框只用来「添加一项」，所以每次进来都是空的；提示只在空且未聚焦时显示。
        this.addBox = new EditBox(this.font, this.panelLeft, this.addBoxY, PANEL_WIDTH - 26, 20,
                Component.translatable("simpleoffhand.config.twoHandedItems"));
        this.addBox.setHint(Component.literal("minecraft:filled_map"));
        this.addBox.setMaxLength(128);
        addRenderableWidget(this.addBox);

        // 常亮：空输入时点击没反应即可，不要做成灰按钮
        this.addButton = Button.builder(Component.literal("+"), b -> addTypedItem())
                .bounds(this.panelLeft + PANEL_WIDTH - 22, this.addBoxY, 22, 20).build();
        addRenderableWidget(this.addButton);

        this.prevButton = Button.builder(Component.literal("<"), b -> turnPage(-1))
                .bounds(this.panelLeft + PANEL_WIDTH - 44, listLabelY() - 2, 20, 16).build();
        addRenderableWidget(this.prevButton);

        this.nextButton = Button.builder(Component.literal(">"), b -> turnPage(1))
                .bounds(this.panelLeft + PANEL_WIDTH - 22, listLabelY() - 2, 20, 16).build();
        addRenderableWidget(this.nextButton);

        int footer = footerY();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                .bounds(this.panelLeft, footer, PANEL_WIDTH / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.panelLeft + PANEL_WIDTH / 2 + 2, footer, PANEL_WIDTH / 2 - 2, 20).build());

        buildRows();
        updatePaging();
    }

    // ------------------------------------------------------------------ 布局

    private int contentTop() {
        // 别和标题撞上
        return Math.max((this.height - CONTENT_HEIGHT) / 2, this.height / 6 + 24);
    }

    private int listLabelY() {
        return contentTop() + 20 + 8;
    }

    private int footerY() {
        int top = listLabelY() + LABEL_HEIGHT + 2;
        return top + VISIBLE_ROWS * ROW_HEIGHT + 8 + 20 + 8;
    }

    private void layout() {
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.listTop = listLabelY() + LABEL_HEIGHT + 2;
        this.addBoxY = this.listTop + VISIBLE_ROWS * ROW_HEIGHT + 8;
    }

    // ---------------------------------------------------------------- 列表行

    /** 按当前 items 重建删除按钮；只在 init() / rebuildRows() 里调用。 */
    private void buildRows() {
        this.removeButtons.clear();
        for (int i = 0; i < this.items.size(); i++) {
            final int index = i;
            Button remove = Button.builder(Component.literal("x"), b -> removeItem(index))
                    .bounds(0, 0, 20, 18).build();
            addRenderableWidget(remove);
            this.removeButtons.add(remove);
        }
    }

    /** items 变化后重建行按钮。不能整个重跑 init()，否则输入框内容会丢。 */
    private void rebuildRows() {
        for (Button remove : this.removeButtons) {
            removeWidget(remove);
        }
        buildRows();
        positionRows();
        updatePaging();
    }

    /** 每帧把可见行摆到当前位置，并隐藏不在本页的行。 */
    private void positionRows() {
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));

        for (int i = 0; i < this.removeButtons.size(); i++) {
            Button remove = this.removeButtons.get(i);
            int slot = i - this.scroll;
            boolean onPage = slot >= 0 && slot < VISIBLE_ROWS;
            remove.visible = onPage;
            remove.active = onPage;
            if (onPage) {
                remove.setX(this.panelLeft + PANEL_WIDTH - 20);
                remove.setY(this.listTop + slot * ROW_HEIGHT + 2);
            }
        }
    }

    private int maxScroll() {
        return Math.max(0, this.items.size() - VISIBLE_ROWS);
    }

    private void turnPage(int direction) {
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll + direction * VISIBLE_ROWS));
        positionRows();
        updatePaging();
    }

    private void updatePaging() {
        boolean many = this.items.size() > VISIBLE_ROWS;
        if (this.prevButton != null) {
            this.prevButton.visible = many;
            this.prevButton.active = many && this.scroll > 0;
        }
        if (this.nextButton != null) {
            this.nextButton.visible = many;
            this.nextButton.active = many && this.scroll < maxScroll();
        }
    }

    // ------------------------------------------------------------------ 增删

    private void addTypedItem() {
        String value = this.addBox.getValue().trim();
        if (value.isEmpty() || this.items.contains(value)) {
            return;
        }
        this.items.add(value);
        this.addBox.setValue("");

        // 新增项可能在最后一页，直接翻过去，免得用户以为没加上
        this.scroll = maxScroll();
        rebuildRows();
    }

    private void removeItem(int index) {
        if (index < 0 || index >= this.items.size()) {
            return;
        }
        this.items.remove(index);
        rebuildRows();
    }

    // ------------------------------------------------------------------ 保存

    private Component toggleLabel() {
        return CommonComponents.optionNameValue(
                Component.translatable("simpleoffhand.config.modEnabled"),
                CommonComponents.optionStatus(this.enabled));
    }

    private void save() {
        SimpleOffhandConfig.setEnabled(this.enabled);
        SimpleOffhandConfig.setTwoHandedItems(new ArrayList<>(this.items));
        SimpleOffhandConfig.save();
        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    // ------------------------------------------------------------------ 渲染

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // 先铺一块自己的面板，避免文字直接压在模糊背景上显得发虚
        int top = contentTop();
        graphics.fill(this.panelLeft - 10, top - 26,
                this.panelLeft + PANEL_WIDTH + 10, footerY() + 30, COLOR_PANEL);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, top - 18, COLOR_TITLE);
        graphics.drawString(this.font,
                Component.translatable("simpleoffhand.config.twoHandedItems"),
                this.panelLeft, listLabelY(), COLOR_LABEL);

        if (this.items.size() > VISIBLE_ROWS) {
            String page = (this.scroll / VISIBLE_ROWS + 1) + "/"
                    + ((this.items.size() + VISIBLE_ROWS - 1) / VISIBLE_ROWS);
            graphics.drawString(this.font, Component.literal(page),
                    this.panelLeft + 120, listLabelY(), COLOR_LABEL);
        }

        positionRows();
        renderRows(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics graphics) {
        if (this.items.isEmpty()) {
            graphics.drawString(this.font, Component.literal("(empty)"),
                    this.panelLeft + 2, this.listTop + 6, COLOR_HINT);
            return;
        }

        int maxTextWidth = PANEL_WIDTH - 30;
        int last = Math.min(this.items.size(), this.scroll + VISIBLE_ROWS);
        for (int i = this.scroll; i < last; i++) {
            int slot = i - this.scroll;
            int textY = this.listTop + slot * ROW_HEIGHT + (ROW_HEIGHT - 8) / 2;
            String text = this.font.plainSubstrByWidth(this.items.get(i), maxTextWidth);
            graphics.drawString(this.font, Component.literal(text), this.panelLeft + 2, textY, COLOR_ITEM);
        }
    }

    // ------------------------------------------------------------------ 事件

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.addBox != null && this.addBox.isFocused()
                && (keyCode == KEY_ENTER || keyCode == KEY_KP_ENTER)) {
            addTypedItem();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
