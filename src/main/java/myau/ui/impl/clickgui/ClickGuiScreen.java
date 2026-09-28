package myau.ui.impl.clickgui;

import myau.Myau;
import myau.module.Category;
import myau.module.Module;
import myau.module.modules.ClickGUIModule;
import myau.ui.impl.clickgui.element.CategoryElement;
import myau.ui.impl.clickgui.element.ModuleElement;
import myau.util.RenderUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClickGuiScreen extends GuiScreen {
    private static ClickGuiScreen instance;

    private final List<CategoryElement> categories = new ArrayList<>();
    private final List<ModuleElement> modules = new ArrayList<>();
    private Category selected = Category.COMBAT;

    private int guiX, guiY;
    private int scroll;
    private float openAnim;
    private boolean closing;
    private long openTime;

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
        if (Myau.moduleManager == null) return;
        for (Module m : Myau.moduleManager.getModulesInCategory(selected)) {
            modules.add(new ModuleElement(m, 0, 0, Theme.WINDOW_W - Theme.SIDEBAR_W - 16));
        }
        scroll = 0;
    }

    @Override
    public void initGui() {
        closing = false;
        openTime = System.currentTimeMillis();
        openAnim = 0;
        ScaledResolution sr = new ScaledResolution(mc);
        guiX = (sr.getScaledWidth() - Theme.WINDOW_W) / 2;
        guiY = (sr.getScaledHeight() - Theme.WINDOW_H) / 2;
    }

    public void close() {
        if (!closing) {
            closing = true;
            openTime = System.currentTimeMillis();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
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

        // 主背景
        RenderUtil.drawRoundedRect(guiX, guiY, Theme.WINDOW_W, Theme.WINDOW_H, Theme.RADIUS,
                Theme.rgba(Theme.BG, (int)(255 * alpha)), true, true, true, true);

        // 側邊欄
        RenderUtil.drawRoundedRect(guiX, guiY, Theme.SIDEBAR_W, Theme.WINDOW_H, Theme.RADIUS,
                Theme.rgba(Theme.SIDEBAR, (int)(255 * alpha)), true, false, true, false);

        // Categories
        int cy = guiY + 12;
        for (CategoryElement cat : categories) {
            cat.x = guiX;
            cat.y = cy;
            cat.setSelected(cat.getCategory() == selected);
            cat.render(mouseX, mouseY, partialTicks, alpha);
            cy += Theme.CAT_H + 2;
        }

        // 右側 Modules
        int contentX = guiX + Theme.SIDEBAR_W + 8;
        int contentY = guiY + 10;
        int contentW = Theme.WINDOW_W - Theme.SIDEBAR_W - 16;
        int contentH = Theme.WINDOW_H - 20;

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

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll += wheel > 0 ? -20 : 20;
            scroll = Math.max(0, scroll);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        // Categories
        for (CategoryElement cat : categories) {
            if (cat.mouseClicked(mouseX, mouseY, button)) {
                selected = cat.getCategory();
                rebuildModules();
                return;
            }
        }
        // Modules
        for (ModuleElement mod : modules) {
            if (mod.mouseClicked(mouseX, mouseY, button)) return;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        for (ModuleElement mod : modules) {
            mod.mouseReleased(mouseX, mouseY, state);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        boolean binding = false;
        for (ModuleElement mod : modules) {
            if (mod.isBinding()) {
                binding = true;
                mod.keyTyped(typedChar, keyCode);
            }
        }
        if (binding) return;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
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
        Module gui = Myau.moduleManager.getModule("ClickGUI");
        if (gui != null) gui.setEnabled(false);
    }
}
