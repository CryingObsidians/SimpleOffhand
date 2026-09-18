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
 * <h2>为什么不用任何原版控件，也不调 super.render()</h2>
 *
 * <p>起初用的是 {@code addRenderableWidget} + {@code Button}/{@code EditBox}，靠
 * {@code super.render()} 画控件。实测在游戏里这套控件**一个都没显示出来**（只有
 * {@code render} 里手写的文字画出来了），所以现在改成：<b>全部自己画、自己处理点击</b>，
 * 一个原版控件都不用。</p>
 *
 * <p>由此还带来一个必须注意的点：<b>{@code render} 里不能再调 {@code super.render(...)}</b>。
 * {@code Screen#render} 的第一步就是 {@code renderBackground} →
 * {@code renderBlurredBackground} → {@code GameRenderer.processBlurEffect}，
 * 那是针对主渲染目标的<b>后处理模糊</b>（半径由「菜单背景模糊程度」决定，
 * 默认 0.5 → 半径 5.0）。它会把整帧里已经画好的内容一起糊掉，表现就是整个配置界面蒙上一层
 * 毛玻璃，而且被凸显的按钮其实在它下面、根本凸显不出来。本界面没有任何注册进
 * {@code renderables} 的原版控件，所以省掉 {@code super.render()} 既无副作用，
 * 又能彻底避免那次模糊。</p>
 *
 * <p>为了三条件共用同一份代码，这里也刻意不用 {@code ObjectSelectionList}
 * （它的 {@code Entry#render} 参数个数在 1.20.1/1.20.4 之间改过，
 * {@code mouseScrolled} 的签名也不同）。</p>
 */
public class SimpleOffhandConfigScreen extends Screen {

    // ---- 布局（全部按屏幕尺寸等比缩放，不留任何绝对坐标）----
    /** 设计基准尺寸：面板宽度和最大行数，实际尺寸由 scale 缩放到屏幕内。 */
    private static final int DESIGN_WIDTH = 220;
    private static final int DESIGN_ROWS = 6;

    /** 屏幕内的最小值/最大值，保证缩放后仍可点。 */
    private static final int MIN_ROW_HEIGHT = 9;
    private static final int MAX_ROW_HEIGHT = 20;
    private static final int MIN_BUTTON_HEIGHT = 10;
    private static final int MAX_BUTTON_HEIGHT = 20;

    // 这些都由 refreshLayout() 按当前屏幕算出
    private int panelWidth;
    private int rowHeight;
    private int buttonHeight;
    private int gap;
    private int labelHeight;
    private int pad;
    private int visibleRows;

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
        return this.top + this.buttonHeight + this.gap;
    }

    private int rowAreaTop() {
        return labelList() + this.labelHeight + 2;
    }

    private int inputY() {
        return rowAreaTop() + this.visibleRows * this.rowHeight + this.gap;
    }

    private int addY() {
        return inputY() + this.buttonHeight + this.gap;
    }

    private int footerY() {
        return addY() + this.buttonHeight + this.gap;
    }

    private int contentHeight() {
        return this.buttonHeight + this.gap                                   // 开关
                + this.labelHeight + 2 + this.visibleRows * this.rowHeight    // 列表
                + this.gap + this.buttonHeight                                // 输入框
                + this.gap + this.buttonHeight                                // 添加
                + this.gap + this.buttonHeight;                               // 完成 / 取消
    }

    private int maxScroll() {
        return Math.max(0, this.items.size() - this.visibleRows);
    }

    /**
     * 按当前屏幕尺寸等比缩放出一套尺寸，并按可见高度决定显示几行。
     *
     * <p>之前用的是固定像素 + 屏幕居中，窗口一拉宽变矮，下面的按钮就跑出屏幕外，
     * 点也点不到。现在所有尺寸都由屏幕尺寸推出来，并且先保证「开关 / 列表 / 输入框 /
     * 添加 / 完成取消」这一整块能塞进屏幕，塞不下就少显示几行、再不行就整体缩小。</p>
     */
    private void refreshLayout() {
        int safeWidth = Math.max(120, this.width - 20);
        int safeHeight = Math.max(80, this.height - 16);

        this.panelWidth = Math.min(DESIGN_WIDTH, safeWidth);
        this.pad = Math.max(2, this.panelWidth / 16);
        this.gap = clamp(this.panelWidth / 40, 2, 6);
        this.labelHeight = clamp(this.panelWidth / 22, 8, 10);
        this.buttonHeight = clamp(this.panelWidth / 10, MIN_BUTTON_HEIGHT, MAX_BUTTON_HEIGHT);

        // 先按最大行数算，塞不进就减行，再塞不进就压行高
        this.visibleRows = DESIGN_ROWS;
        this.rowHeight = MAX_ROW_HEIGHT;
        int fixedHeight = this.buttonHeight * 4 + this.gap * 4 + this.labelHeight + 2;
        int available = safeHeight - fixedHeight - this.labelHeight - 4;

        if (available < this.visibleRows * MIN_ROW_HEIGHT) {
            int fits = available / MIN_ROW_HEIGHT;
            this.visibleRows = clamp(fits, 1, DESIGN_ROWS);
            this.rowHeight = MIN_ROW_HEIGHT;
        } else if (available < this.visibleRows * MAX_ROW_HEIGHT) {
            this.rowHeight = clamp(available / this.visibleRows, MIN_ROW_HEIGHT, MAX_ROW_HEIGHT);
        }

        // 标题单独占一行，放在面板上方
        int titleRow = labelHeight + 4;
        int totalHeight = titleRow + contentHeight();
        this.top = Math.max(2, (this.height - totalHeight) / 2) + titleRow;
        this.left = (this.width - this.panelWidth) / 2;
        this.listTop = rowAreaTop();

        this.toggleRect = new int[] { this.left, this.top, this.panelWidth, this.buttonHeight };
        this.inputRect = new int[] { this.left, inputY(), this.panelWidth, this.buttonHeight };
        this.addRect = new int[] { this.left, addY(), this.panelWidth, this.buttonHeight };

        int halfWidth = (this.panelWidth - this.gap) / 2;
        this.doneRect = new int[] { this.left, footerY(), halfWidth, this.buttonHeight };
        this.cancelRect = new int[] { this.left + this.panelWidth - halfWidth, footerY(),
                halfWidth, this.buttonHeight };

        int arrowW = clamp(this.panelWidth / 11, 12, 20);
        int arrowH = this.labelHeight;
        int arrowY = labelList();
        this.prevRect = new int[] { this.left + this.panelWidth - arrowW * 2 - 2, arrowY, arrowW, arrowH };
        this.nextRect = new int[] { this.left + this.panelWidth - arrowW, arrowY, arrowW, arrowH };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void init() {
        refreshLayout();
        rebuildRemoveRects();
    }

    private void rebuildRemoveRects() {
        this.removeRects.clear();
        int removeW = this.buttonHeight;
        int removeH = Math.max(8, this.rowHeight - 2);
        for (int i = 0; i < this.items.size(); i++) {
            this.removeRects.add(new int[] {
                    this.left + this.panelWidth - removeW - 2, 0, removeW, removeH });
        }
        positionRemoveRects();
    }

    private void positionRemoveRects() {
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
        for (int i = 0; i < this.removeRects.size(); i++) {
            int slot = i - this.scroll;
            int[] r = this.removeRects.get(i);
            boolean onPage = slot >= 0 && slot < this.visibleRows;
            r[1] = onPage ? this.listTop + slot * this.rowHeight + 1 : Integer.MIN_VALUE;
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

        if (this.items.size() > this.visibleRows) {
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
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll + direction * this.visibleRows));
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
        // 自己铺不透明底。注意不能改用 renderBackground：见类注释，
        // 它会触发后处理模糊，把整帧已经画好的内容一起糊掉。
        g.fill(0, 0, this.width, this.height, COLOR_SCRIM);

        int panelBottom = footerY() + this.buttonHeight + 10;
        g.fill(this.left - 10, this.top - 28, this.left + this.panelWidth + 10, panelBottom, COLOR_PANEL);

        g.drawCenteredString(this.font, this.title, this.width / 2, this.top - 20, COLOR_TITLE);

        // 开关
        drawButton(g, this.toggleRect, toggleLabel(), mouseX, mouseY);

        // 列表标签
        g.drawString(this.font, Component.translatable("simpleoffhand.config.twoHandedItems"),
                this.left, labelList() + 2, COLOR_LABEL);
        if (this.items.size() > this.visibleRows) {
            String page = (this.scroll / this.visibleRows + 1) + "/"
                    + ((this.items.size() + this.visibleRows - 1) / this.visibleRows);
            g.drawString(this.font, Component.literal(page), this.left + this.panelWidth - 90,
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

        // 这里**绝对不能调 super.render(...)**。
        // Screen#render 的第一步就是 renderBackground -> renderBlurredBackground ->
        // GameRenderer.processBlurEffect，那是对主渲染目标做后处理模糊，会把整帧已经画好的
        // 内容一起糊掉 —— 表现就是整个配置界面蒙一层毛玻璃（后面什么都没有被凸显出来）。
        // 本界面完全没有用 addRenderableWidget 注册的原版控件，所以 super.render 无事可做，
        // 省掉它既没有副作用，又能彻底避开那次模糊。
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

        int last = Math.min(this.items.size(), this.scroll + this.visibleRows);
        for (int i = this.scroll; i < last; i++) {
            int slot = i - this.scroll;
            int rowY = this.listTop + slot * this.rowHeight;

            String text = this.font.plainSubstrByWidth(this.items.get(i), this.panelWidth - 32);
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
