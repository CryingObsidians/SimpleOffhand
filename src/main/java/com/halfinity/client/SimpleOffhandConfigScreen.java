package com.halfinity.client;

import com.halfinity.config.SimpleOffhandConfig;
import net.minecraft.client.gui.GuiGraphics;
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
 * <h2>为什么不用任何原版控件</h2>
 *
 * <p>起初用的是 {@code addRenderableWidget} + {@code Button}/{@code EditBox}，靠
 * {@code super.render()} 画控件。实测在游戏里这套控件**一个都没显示出来**（只有
 * {@code render} 里手写的文字画出来了），所以现在改成：<b>全部自己画、自己处理点击</b>，
 * 一个原版控件都不用。这样渲染路径完全在自己手里，不存在"控件没被画"的可能。</p>
 *
 * <p>为了三条件共用同一份代码，这里也刻意不用 {@code ObjectSelectionList}
 * （它的 {@code Entry#render} 参数个数在 1.20.1/1.20.4 之间改过，
 * {@code mouseScrolled} 的签名也不同）。</p>
 *
 * <p>与版本相关的差异只剩 {@code renderBackground} 的签名
 * （1.20.1 是单参，1.20.4 起是四参），已在各分支就地改好。</p>
 */
public class SimpleOffhandConfigScreen extends Screen {

    // ---- 布局（整块内容在屏幕中垂直居中）----
    private static final int PANEL_WIDTH = 220;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;
    private static final int LABEL_HEIGHT = 10;

    private static final int COLOR_SCRIM = 0xFF000000;     // 最底下一层，保证不透明
    private static final int COLOR_PANEL = 0xFF1A1A1A;     // 面板
    private static final int COLOR_BUTTON = 0xFF4A4A4A;    // 按钮本体
    private static final int COLOR_BUTTON_HOVER = 0xFF6A6A6A;
    private static final int COLOR_FIELD = 0xFF0A0A0A;     // 输入框
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_LABEL = 0xFFC0C0C0;
    private static final int COLOR_ITEM = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFF808080;

    /** Enter / Backspace / Delete 的键码。 */
    private static final int KEY_ENTER = 257;
    private static final int KEY_KP_ENTER = 335;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;

    private final Screen parent;

    /** 界面上的临时状态，点「完成」时才写回配置。 */
    private boolean enabled;
    private final List<String> items = new ArrayList<>();

    /** 输入框内容与光标状态（自己实现，不用 EditBox）。 */
    private String input = "";
    private boolean inputFocused = true;

    /** 列表当前显示的第一项下标。 */
    private int scroll = 0;

    // 由 layout() 算出
    private int left;
    private int top;
    private int listTop;

    // 手动 hit-test 用的矩形（x, y, w, h）
    private int[] toggleRect;
    private int[] inputRect;
    private int[] addRect;
    private int[] doneRect;
    private int[] cancelRect;
    private int[] prevRect;
    private int[] nextRect;
    private final List<int[]> removeRects = new ArrayList<>();

    /** ConfigScreenHandler.ConfigScreenFactory 要求的构造器签名。 */
    public SimpleOffhandConfigScreen(net.minecraft.client.Minecraft minecraft, Screen parent) {
        super(Component.translatable("simpleoffhand.configuration.title"));
        this.parent = parent;
        this.enabled = SimpleOffhandConfig.isEnabled();
        this.items.addAll(SimpleOffhandConfig.getTwoHandedItems());
    }

    // ------------------------------------------------------------------ 布局

    private int labelList() {
        return top + BUTTON_HEIGHT + GAP;
    }

    private int rowAreaTop() {
        return labelList() + LABEL_HEIGHT + 2;
    }

    private int inputY() {
        return rowAreaTop() + VISIBLE_ROWS * ROW_HEIGHT + GAP;
    }

    private int addY() {
        return inputY() + BUTTON_HEIGHT + GAP;
    }

    private int footerY() {
        return addY() + BUTTON_HEIGHT + GAP;
    }

    private static int contentHeight() {
        return BUTTON_HEIGHT + GAP                    // 开关
                + LABEL_HEIGHT + 2 + VISIBLE_ROWS * ROW_HEIGHT   // 列表
                + GAP + BUTTON_HEIGHT                 // 输入框
                + GAP + BUTTON_HEIGHT                 // 添加
                + GAP + BUTTON_HEIGHT;                // 完成 / 取消
    }

    private int maxScroll() {
        return Math.max(0, this.items.size() - VISIBLE_ROWS);
    }

    private void layout() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        int contentHeight = contentHeight() + 2 * (LABEL_HEIGHT + 6);
        this.top = Math.max((this.height - contentHeight) / 2, this.height / 6 + 24);
        this.listTop = rowAreaTop();

        this.toggleRect = new int[] { this.left, this.top, PANEL_WIDTH, BUTTON_HEIGHT };
        this.inputRect = new int[] { this.left, inputY(), PANEL_WIDTH, BUTTON_HEIGHT };
        this.addRect = new int[] { this.left, addY(), PANEL_WIDTH, BUTTON_HEIGHT };
        this.doneRect = new int[] { this.left, footerY(), PANEL_WIDTH / 2 - 2, BUTTON_HEIGHT };
        this.cancelRect = new int[] { this.left + PANEL_WIDTH / 2 + 2, footerY(),
                PANEL_WIDTH / 2 - 2, BUTTON_HEIGHT };
        this.prevRect = new int[] { this.left + PANEL_WIDTH - 44, labelList(), 20, LABEL_HEIGHT + 2 };
        this.nextRect = new int[] { this.left + PANEL_WIDTH - 22, labelList(), 20, LABEL_HEIGHT + 2 };
    }

    @Override
    protected void init() {
        layout();
        rebuildRemoveRects();
    }

    private void rebuildRemoveRects() {
        this.removeRects.clear();
        for (int i = 0; i < this.items.size(); i++) {
            this.removeRects.add(new int[] { this.left + PANEL_WIDTH - 18, 0, 18, ROW_HEIGHT - 2 });
        }
        positionRemoveRects();
    }

    private void positionRemoveRects() {
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
        for (int i = 0; i < this.removeRects.size(); i++) {
            int slot = i - this.scroll;
            int[] r = this.removeRects.get(i);
            boolean onPage = slot >= 0 && slot < VISIBLE_ROWS;
            r[1] = onPage ? this.listTop + slot * ROW_HEIGHT + 1 : Integer.MIN_VALUE;
        }
    }

    // ------------------------------------------------------------------ 点击

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // 输入框：点它 = 聚焦
        if (hit(this.inputRect, mouseX, mouseY)) {
            this.inputFocused = true;
            return true;
        }

        if (hit(this.toggleRect, mouseX, mouseY)) {
            this.enabled = !this.enabled;
            return true;
        }

        if (hit(this.addRect, mouseX, mouseY)) {
            addTypedItem();
            return true;
        }

        if (this.items.size() > VISIBLE_ROWS) {
            if (hit(this.prevRect, mouseX, mouseY)) {
                turnPage(-1);
                return true;
            }
            if (hit(this.nextRect, mouseX, mouseY)) {
                turnPage(1);
                return true;
            }
        }

        // 每行的删除按钮
        for (int i = 0; i < this.removeRects.size(); i++) {
            if (hit(this.removeRects.get(i), mouseX, mouseY)) {
                this.items.remove(i);
                this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
                rebuildRemoveRects();
                return true;
            }
        }

        if (hit(this.doneRect, mouseX, mouseY)) {
            save();
            return true;
        }

        if (hit(this.cancelRect, mouseX, mouseY)) {
            onClose();
            return true;
        }

        // 点空白处 = 输入框失焦
        this.inputFocused = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean hit(int[] r, double mx, double my) {
        if (r == null || r[1] == Integer.MIN_VALUE) {
            return false;
        }
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    // ------------------------------------------------------------------ 键盘

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.inputFocused) {
            if (keyCode == KEY_ENTER || keyCode == KEY_KP_ENTER) {
                addTypedItem();
                return true;
            }
            if (keyCode == KEY_BACKSPACE || keyCode == KEY_DELETE) {
                if (!this.input.isEmpty()) {
                    this.input = this.input.substring(0, this.input.length() - 1);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.inputFocused && this.input.length() < 64
                && codePoint >= 32 && codePoint != 127) {
            this.input += codePoint;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ 增删

    private void addTypedItem() {
        String value = this.input.trim();
        if (value.isEmpty() || this.items.contains(value)) {
            return;
        }
        this.items.add(value);
        this.input = "";

        // 新增项可能在最后一页，直接翻过去
        this.scroll = maxScroll();
        rebuildRemoveRects();
    }

    private void turnPage(int direction) {
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll + direction * VISIBLE_ROWS));
        positionRemoveRects();
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 注意：这里**故意不调 renderBackground**。
        // 1.20.6 的 renderBackground 会走 renderBlurredBackground ->
        // GameRenderer.processBlurEffect，那是后处理模糊，会把当前渲染目标里**已经画好的东西
        // 一起糊掉**，于是整个配置界面都像蒙了一层毛玻璃。改成自己铺不透明底。
        g.fill(0, 0, this.width, this.height, COLOR_SCRIM);

        int panelBottom = footerY() + BUTTON_HEIGHT + 10;
        g.fill(this.left - 10, this.top - 28, this.left + PANEL_WIDTH + 10, panelBottom, COLOR_PANEL);

        g.drawCenteredString(this.font, this.title, this.width / 2, this.top - 20, COLOR_TITLE);

        // 开关
        drawButton(g, this.toggleRect, toggleLabel(), mouseX, mouseY);

        // 列表标签
        g.drawString(this.font, Component.translatable("simpleoffhand.config.twoHandedItems"),
                this.left, labelList() + 2, COLOR_LABEL);
        if (this.items.size() > VISIBLE_ROWS) {
            String page = (this.scroll / VISIBLE_ROWS + 1) + "/"
                    + ((this.items.size() + VISIBLE_ROWS - 1) / VISIBLE_ROWS);
            g.drawString(this.font, Component.literal(page), this.left + PANEL_WIDTH - 90,
                    labelList() + 2, COLOR_LABEL);
            drawButton(g, this.prevRect, Component.literal("<"), mouseX, mouseY);
            drawButton(g, this.nextRect, Component.literal(">"), mouseX, mouseY);
        }

        positionRemoveRects();
        drawRows(g, mouseX, mouseY);

        // 输入框
        drawField(g, mouseX, mouseY);
        drawButton(g, this.addRect, Component.translatable("simpleoffhand.config.add"), mouseX, mouseY);

        // 底部
        drawButton(g, this.doneRect, CommonComponents.GUI_DONE, mouseX, mouseY);
        drawButton(g, this.cancelRect, CommonComponents.GUI_CANCEL, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private Component toggleLabel() {
        return CommonComponents.optionNameValue(
                Component.translatable("simpleoffhand.config.modEnabled"),
                CommonComponents.optionStatus(this.enabled));
    }

    private void drawRows(GuiGraphics g, int mouseX, int mouseY) {
        if (this.items.isEmpty()) {
            g.drawString(this.font, Component.translatable("simpleoffhand.config.empty"),
                    this.left + 4, this.listTop + 6, COLOR_HINT);
            return;
        }

        int last = Math.min(this.items.size(), this.scroll + VISIBLE_ROWS);
        for (int i = this.scroll; i < last; i++) {
            int slot = i - this.scroll;
            int rowY = this.listTop + slot * ROW_HEIGHT;

            String text = this.font.plainSubstrByWidth(this.items.get(i), PANEL_WIDTH - 32);
            g.drawString(this.font, Component.literal(text), this.left + 4, rowY + 6, COLOR_ITEM);

            drawButton(g, this.removeRects.get(i), Component.literal("x"), mouseX, mouseY);
        }
    }

    private void drawField(GuiGraphics g, int mouseX, int mouseY) {
        int[] r = this.inputRect;
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], COLOR_FIELD);
        drawBorder(g, r, this.inputFocused ? 0xFFCFCFCF : 0xFF707070);

        if (this.input.isEmpty()) {
            g.drawString(this.font, Component.literal("minecraft:filled_map"),
                    r[0] + 5, r[1] + 6, COLOR_HINT);
        } else {
            g.drawString(this.font, Component.literal(this.input),
                    r[0] + 5, r[1] + 6, COLOR_ITEM);
        }
    }

    /** 自己画的按钮：底色 + 居中文字，鼠标悬停变色。 */
    private void drawButton(GuiGraphics g, int[] r, Component label, int mouseX, int mouseY) {
        if (r == null || r[1] == Integer.MIN_VALUE) {
            return;
        }
        boolean hovered = hit(r, mouseX, mouseY);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        drawBorder(g, r, hovered ? 0xFFFFFFFF : 0xFF909090);

        int textWidth = this.font.width(label);
        int textX = r[0] + (r[2] - textWidth) / 2;
        int textY = r[1] + (r[3] - 8) / 2;
        // 文字可能比按钮宽，裁一下免得溢出
        String clipped = this.font.plainSubstrByWidth(label.getString(), r[2] - 4);
        g.drawString(this.font, Component.literal(clipped),
                r[0] + (r[2] - this.font.width(clipped)) / 2, textY, 0xFFFFFFFF);
    }

    private void drawBorder(GuiGraphics g, int[] r, int color) {
        g.fill(r[0], r[1], r[0] + r[2], r[1] + 1, color);
        g.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], color);
        g.fill(r[0], r[1], r[0] + 1, r[1] + r[3], color);
        g.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], color);
    }
}
