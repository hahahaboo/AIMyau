package myau.ui.impl.clickgui;

import myau.Myau;
import myau.module.Category;
import myau.module.Module;
import myau.module.modules.ClickGUIModule;
import myau.ui.impl.clickgui.element.CategoryElement;
import myau.ui.impl.clickgui.element.Element;
import myau.ui.impl.clickgui.element.ModuleElement;
import myau.ui.impl.clickgui.element.setting.ColorElement;
import myau.util.RenderUtil;
import myau.util.shader.ShadowShader;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClickGuiScreen extends GuiScreen {
    private static ClickGuiScreen instance;

    /** 僅儲存位置（Save GUI State） */
    private static int savedX = Integer.MIN_VALUE;
    private static int savedY = Integer.MIN_VALUE;

    /** 頂部可拖曳空白高度（無標題列外觀） */
    private static final int DRAG_H = 12;
    private static final float SHADOW_SOFTNESS = 12.0f;
    private static final int SHADOW_ALPHA = 100;

    private final List<CategoryElement> categories = new ArrayList<>();
    private final List<ModuleElement> modules = new ArrayList<>();
    private Category selected = Category.COMBAT;

    private int guiX, guiY;
    private int scroll;
    private float openAnim;
    private boolean closing;
    private long openTime;
    private long lastFrameTime;

    private boolean dragging;
    private int dragOffsetX, dragOffsetY;

    public static ClickGuiScreen getInstance() {
        if (instance == null) instance = new ClickGuiScreen();
        return instance;
    }

    public ClickGuiScreen() {
        for (Category c : Category.values()) {
            categories.add(new CategoryElement(c, 0, 0, Theme.SIDEBAR_W, Theme.CAT_H));
        }
        rebuildModules();
    }

    private void rebuildModules() {
        modules.clear();
        ColorElement.closePicker();
        if (Myau.moduleManager == null) return;
        for (Module m : Myau.moduleManager.getModulesInCategory(selected)) {
            modules.add(new ModuleElement(m, 0, 0, Theme.WINDOW_W - Theme.SIDEBAR_W - 16));
        }
        scroll = 0;
    }

    private int getMaxScroll() {
        int contentH = Theme.WINDOW_H - DRAG_H - 14;
        int totalH = 0;
        for (ModuleElement mod : modules) {
            totalH += (int) mod.getCurrentHeight() + 4;
        }
        if (totalH > 0) totalH -= 4;
        return Math.max(0, totalH - contentH);
    }

    private boolean isSavePositionEnabled() {
        try {
            ClickGUIModule mod = (ClickGUIModule) Myau.moduleManager.getModule("ClickGUI");
            return mod != null && mod.saveGuiState.getValue();
        } catch (Exception e) {
            return true;
        }
    }

    private void savePosition() {
        if (isSavePositionEnabled()) {
            savedX = guiX;
            savedY = guiY;
        }
    }

    private void clampToScreen() {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        // 整個視窗不得超出螢幕四邊
        guiX = Math.max(0, Math.min(guiX, sw - Theme.WINDOW_W));
        guiY = Math.max(0, Math.min(guiY, sh - Theme.WINDOW_H));
    }

    /** 僅頂部空白可拖曳 */
    private boolean isInsideDragArea(int mouseX, int mouseY) {
        return mouseX >= guiX && mouseX <= guiX + Theme.WINDOW_W
                && mouseY >= guiY && mouseY <= guiY + DRAG_H;
    }

    @Override
    public void initGui() {
        closing = false;
        dragging = false;
        ColorElement.closePicker();
        openTime = System.currentTimeMillis();
        openAnim = 0;
        lastFrameTime = System.nanoTime();

        ScaledResolution sr = new ScaledResolution(mc);
        if (isSavePositionEnabled() && savedX != Integer.MIN_VALUE && savedY != Integer.MIN_VALUE) {
            guiX = savedX;
            guiY = savedY;
            clampToScreen();
        } else {
            guiX = (sr.getScaledWidth() - Theme.WINDOW_W) / 2;
            guiY = (sr.getScaledHeight() - Theme.WINDOW_H) / 2;
        }
    }

    public void close() {
        if (!closing) {
            closing = true;
            for (ModuleElement mod : modules) {
                mod.cancelTextFocus();
            }
            openTime = System.currentTimeMillis();
            savePosition();
            ColorElement.closePicker();
        }
    }

    private void handleInvWalk() {
        KeyBinding[] keys = {
                mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSprint,
                mc.gameSettings.keyBindSneak
        };

        // 正在打字：先鬆開所有移動鍵，並禁止本幀寫入
        for (ModuleElement mod : modules) {
            if (mod.isTextFocused()) {
                for (KeyBinding key : keys) {
                    KeyBinding.setKeyBindState(key.getKeyCode(), false);
                }
                return;
            }
        }

        if (Myau.moduleManager == null) return;
        Module invWalk = Myau.moduleManager.getModule("InvWalk");
        if (invWalk == null || !invWalk.isEnabled()) return;

        for (KeyBinding key : keys) {
            KeyBinding.setKeyBindState(key.getKeyCode(), Keyboard.isKeyDown(key.getKeyCode()));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // deltaTime
        long now = System.nanoTime();
        float dt = (now - lastFrameTime) / 1_000_000_000.0f;
        lastFrameTime = now;
        if (dt < 0.001f) dt = 0.001f;
        if (dt > 0.05f) dt = 0.05f;
        Element.deltaTime = dt;

        long elapsed = System.currentTimeMillis() - openTime;
        float t = Math.min(1f, elapsed / 200f);
        openAnim = closing ? 1f - t : t;
        openAnim = (float) (1.0 - Math.pow(1.0 - openAnim, 3));

        if (closing && t >= 1f) {
            mc.displayGuiScreen(null);
            return;
        }

        float alpha = openAnim;
        if (alpha < 0.01f) return;

        if (dragging) {
            guiX = mouseX - dragOffsetX;
            guiY = mouseY - dragOffsetY;
            clampToScreen();
        }

        // 固定陰影
        int shadowColor = new Color(0, 0, 0, (int) (SHADOW_ALPHA * alpha)).getRGB();
        ShadowShader.drawShadow(guiX, guiY, Theme.WINDOW_W, Theme.WINDOW_H,
                Theme.RADIUS, SHADOW_SOFTNESS, shadowColor);

        // 主背景
        RenderUtil.drawRoundedRect(guiX, guiY, Theme.WINDOW_W, Theme.WINDOW_H, Theme.RADIUS,
                Theme.rgba(Theme.BG, (int) (255 * alpha)), true, true, true, true);

        // 側邊欄
        RenderUtil.drawRoundedRect(guiX, guiY, Theme.SIDEBAR_W, Theme.WINDOW_H, Theme.RADIUS,
                Theme.rgba(Theme.SIDEBAR, (int) (255 * alpha)), true, false, true, false);

        // Categories（頂部空出 DRAG_H）
        int cy = guiY + DRAG_H + 6;
        for (CategoryElement cat : categories) {
            cat.x = guiX;
            cat.y = cy;
            cat.setSelected(cat.getCategory() == selected);
            cat.render(mouseX, mouseY, partialTicks, alpha);
            cy += Theme.CAT_H + 2;
        }

        // Modules（同樣下移）
        int contentX = guiX + Theme.SIDEBAR_W + 8;
        int contentY = guiY + DRAG_H + 6;
        int contentW = Theme.WINDOW_W - Theme.SIDEBAR_W - 16;
        int contentH = Theme.WINDOW_H - DRAG_H - 14;

        scroll = Math.max(0, Math.min(scroll, getMaxScroll()));

        RenderUtil.scissor(contentX, contentY, contentW, contentH);
        int my = contentY - scroll;
        for (ModuleElement mod : modules) {
            mod.x = contentX;
            mod.y = my;
            mod.width = contentW;
            mod.render(mouseX, mouseY, partialTicks, alpha);
            my += (int) mod.getCurrentHeight() + 4;
        }
        RenderUtil.releaseScissor();

        // modules 已設好 x/y 後
        for (ModuleElement mod : modules) {
            mod.unfocusTextIfOutside(contentX, contentY, contentW, contentH);
        }

        // Color 取色小窗（scissor 外；列被裁切則關閉）
        ColorElement picker = ColorElement.getOpenPicker();
        if (picker != null) {
            boolean rowVisible =
                    picker.x < contentX + contentW && picker.x + picker.width > contentX
                            && picker.y < contentY + contentH && picker.y + picker.height > contentY;

            if (!rowVisible) {
                ColorElement.closePicker();
            } else {
                int popupW = picker.getPopupWidth();
                int popupH = picker.getPopupHeight();
                ScaledResolution sr = new ScaledResolution(mc);
                int px = guiX + Theme.WINDOW_W + 8;
                if (px + popupW > sr.getScaledWidth()) {
                    px = guiX - popupW - 8;
                }
                if (px < 0) px = 0;
                int py = Math.max(0, Math.min(picker.y, sr.getScaledHeight() - popupH));
                picker.setPopupPos(px, py);
                picker.renderPopup(alpha);
            }
        }

        handleInvWalk();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() throws IOException {
        if (closing) return;
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll += wheel > 0 ? -20 : 20;
            scroll = Math.max(0, Math.min(scroll, getMaxScroll()));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (closing) return;

        // Text 先失焦；Slider 由各自 mouseClicked 或點空白後套用
        for (ModuleElement mod : modules) {
            mod.unfocusTextOnly();
        }

        ColorElement picker = ColorElement.getOpenPicker();

        // 點在小窗內 → 只處理取色
        if (picker != null && picker.isInsidePopup(mouseX, mouseY)) {
            // 點取色窗時，把仍 focused 的 slider 套用
            for (ModuleElement mod : modules) {
                mod.applySliderFocusIfStillFocused();
            }
            picker.mouseClickedPopup(mouseX, mouseY, button);
            return;
        }

        boolean handled = false;

        for (CategoryElement cat : categories) {
            if (cat.mouseClicked(mouseX, mouseY, button)) {
                selected = cat.getCategory();
                for (ModuleElement mod : modules) {
                    mod.applySliderFocusIfStillFocused();
                }
                rebuildModules();
                return; // rebuildModules 內已 closePicker
            }
        }

        int contentX = guiX + Theme.SIDEBAR_W + 8;
        int contentY = guiY + DRAG_H + 6;
        int contentW = Theme.WINDOW_W - Theme.SIDEBAR_W - 16;
        int contentH = Theme.WINDOW_H - DRAG_H - 14;

        if (mouseX >= contentX && mouseX < contentX + contentW
                && mouseY >= contentY && mouseY < contentY + contentH) {
            for (ModuleElement mod : modules) {
                if (mod.mouseClicked(mouseX, mouseY, button)) {
                    handled = true;
                    break;
                }
            }
        }

        // 點空白或其他未處理區域 → Slider 套用並失焦
        if (!handled) {
            for (ModuleElement mod : modules) {
                mod.applySliderFocusIfStillFocused();
            }
        }

        // 點在小窗外、且不是色塊 toggle → 關閉小窗
        if (!handled && ColorElement.isPickerOpen()) {
            ColorElement.closePicker();
        }

        if (button == 0 && isInsideDragArea(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - guiX;
            dragOffsetY = mouseY - guiY;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (closing) return;
        if (dragging) {
            dragging = false;
            savePosition();
        }
        ColorElement picker = ColorElement.getOpenPicker();
        if (picker != null) {
            picker.mouseReleased(mouseX, mouseY, state);
        }
        for (ModuleElement mod : modules) {
            mod.mouseReleased(mouseX, mouseY, state);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (closing) return;
        if (dragging) {
            guiX = mouseX - dragOffsetX;
            guiY = mouseY - dragOffsetY;
            clampToScreen();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (closing) return;
        if (System.currentTimeMillis() - this.openTime < 150) return;

        // Text / Slider 輸入優先
        boolean textFocused = false;
        for (ModuleElement mod : modules) {
            if (mod.isTextFocused()) {
                textFocused = true;
                break;
            }
        }
        if (textFocused) {
            // ESC：取消修改並失焦，不關 GUI
            if (keyCode == Keyboard.KEY_ESCAPE) {
                for (ModuleElement mod : modules) mod.cancelTextFocus();
                return;
            }
            for (ModuleElement mod : modules) {
                mod.keyTyped(typedChar, keyCode);
            }
            return;
        }

        // Keybind binding ...
        boolean binding = false;
        for (ModuleElement mod : modules) {
            if (mod.isBinding()) {
                binding = true;
                mod.keyTyped(typedChar, keyCode);
            }
        }
        if (binding) return;

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (ColorElement.isPickerOpen()) {
                ColorElement.closePicker();
                return;
            }
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (ColorElement.isPickerOpen()) {
                ColorElement.closePicker();
            }
            close(); // close() 內已 cancelTextFocus
            return;
        }
        Module guiMod = Myau.moduleManager.getModule("ClickGUI");
        if (guiMod != null && keyCode == guiMod.getKey()) {
            close();
            return;
        }
        for (ModuleElement mod : modules) {
            mod.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        for (ModuleElement mod : modules) {
            mod.cancelTextFocus();
        }
        dragging = false;
        ColorElement.closePicker();
        savePosition();
        Module gui = Myau.moduleManager.getModule("ClickGUI");
        if (gui != null && gui.isEnabled()) {
            gui.setEnabled(false);
        }
    }
}
