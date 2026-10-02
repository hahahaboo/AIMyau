package myau.ui.impl.clickgui.element.setting;

import myau.property.properties.TextProperty;
import myau.ui.impl.clickgui.Theme;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import org.lwjgl.input.Keyboard;

public class TextElement extends SettingElement {
    private final TextProperty prop;
    private boolean focused;

    public TextElement(TextProperty prop, int x, int y, int width) {
        super(x, y, width, Theme.SETTING_H + 6);
        this.prop = prop;
    }

    public boolean isFocused() {
        return focused;
    }

    public void unfocus() {
        focused = false;
    }

    /** 輸入框區域（不含名稱） */
    private boolean isInsideInputBox(int mouseX, int mouseY) {
        int boxY = y + 11;
        int boxH = 12;
        return mouseX >= x + 2 && mouseX <= x + width - 2
                && mouseY >= boxY && mouseY < boxY + boxH;
    }

    @Override
    public boolean isVisible() {
        return prop.isVisible();
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float alpha) {
        if (!isVisible()) return;
        int a = (int) (255 * alpha);

        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(prop.getName(), x + 2, y + 2, Theme.rgba(Theme.TEXT, a));
        }

        int boxY = y + 11;
        int boxH = 12;
        RenderUtil.drawRoundedRect(x + 2, boxY, width - 4, boxH, 3f,
                Theme.rgba(focused ? Theme.MODULE_HOVER : Theme.MODULE, a), true, true, true, true);

        String val = prop.getValue() == null ? "" : prop.getValue().toString();
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(val + (focused ? "_" : ""), x + 5, boxY + 2, Theme.rgba(Theme.TEXT, a));
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        // 右鍵在輸入欄上 → 失焦
        if (button == 1 && isInsideInputBox(mouseX, mouseY)) {
            focused = false;
            return true;
        }
        // 左鍵：只有點在輸入框內才 focus，否則失焦
        if (button == 0) {
            focused = isInsideInputBox(mouseX, mouseY);
            return focused;
        }
        return false;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!focused) return;
        String cur = prop.getValue() == null ? "" : prop.getValue().toString();
        if (keyCode == Keyboard.KEY_BACK && !cur.isEmpty()) {
            prop.setValue(cur.substring(0, cur.length() - 1));
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            focused = false;
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            focused = false;
        } else if (typedChar >= 32 && typedChar < 127) {
            prop.setValue(cur + typedChar);
        }
    }
}
