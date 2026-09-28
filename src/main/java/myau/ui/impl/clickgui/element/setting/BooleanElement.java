package myau.ui.impl.clickgui.element.setting;

import myau.property.properties.BooleanProperty;
import myau.ui.impl.clickgui.Theme;
import myau.ui.impl.clickgui.element.Element;
import myau.util.AnimationUtil;
import myau.util.RenderUtil;
import myau.util.font.FontManager;

public class BooleanElement extends SettingElement {
    private final BooleanProperty prop;
    private float anim;

    public BooleanElement(BooleanProperty prop, int x, int y, int width) {
        super(x, y, width, Theme.SETTING_H);
        this.prop = prop;
        this.anim = prop.getValue() ? 1f : 0f;
    }

    @Override
    public boolean isVisible() {
        return prop.isVisible();
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float alpha) {
        if (!isVisible()) return;
        int a = (int) (255 * alpha);
        anim = AnimationUtil.animateSmooth(prop.getValue() ? 1f : 0f, anim, 14f, Element.deltaTime);

        // 名稱
        int tc = Theme.rgba(Theme.TEXT, a);
        float ty = y + (height - 8) / 2f;
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(prop.getName(), x + 2, ty, tc);
        } else {
            mc.fontRendererObj.drawStringWithShadow(prop.getName(), x + 2, y + 6, tc);
        }

        // Switch
        int sw = 20, sh = 11;
        int sx = x + width - sw - 2;
        int sy = y + (height - sh) / 2;

        int track = Theme.rgba(anim > 0.5f ? Theme.ACCENT : Theme.SWITCH_OFF, a);
        RenderUtil.drawRoundedRect(sx, sy, sw, sh, sh / 2f, track, true, true, true, true);

        int knobX = sx + (int) (anim * (sw - sh));
        RenderUtil.drawRoundedRect(knobX + 1, sy + 1, sh - 2, sh - 2, (sh - 2) / 2f, -1, true, true, true, true);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            prop.setValue(!prop.getValue());
            return true;
        }
        return false;
    }
}
