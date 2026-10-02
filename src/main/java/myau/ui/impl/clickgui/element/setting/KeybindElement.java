package myau.ui.impl.clickgui.element.setting;

import myau.module.Module;
import myau.ui.impl.clickgui.Theme;
import myau.util.KeyBindUtil;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import org.lwjgl.input.Keyboard;

public class KeybindElement extends SettingElement {
    private final Module module;
    private boolean binding;

    public KeybindElement(Module module, int x, int y, int width) {
        super(x, y, width, Theme.SETTING_H);
        this.module = module;
    }

    public boolean isBinding() {
        return binding;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float alpha) {
        int a = (int) (255 * alpha);
        String keyName = binding ? "..." : (module.getKey() == 0 ? "None" : KeyBindUtil.getKeyName(module.getKey()));
        String text = "Keybind: " + keyName;

        int tc = binding ? Theme.rgba(Theme.ACCENT, a) : Theme.rgba(Theme.TEXT, a);
        float ty = y + (height - 8) / 2f;
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(text, x + 2, ty, tc);
        } else {
            mc.fontRendererObj.drawStringWithShadow(text, x + 2, y + 6, tc);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            binding = true;
            return true;
        }
        return false;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!binding) return;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            module.setKey(0);
        } else {
            module.setKey(keyCode);
        }
        binding = false;
    }
}
