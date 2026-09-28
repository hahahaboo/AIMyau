package myau.ui.impl.clickgui.element.setting;

import myau.property.properties.ModeProperty;
import myau.ui.impl.clickgui.Theme;
import myau.ui.impl.clickgui.element.Element;
import myau.util.AnimationUtil;
import myau.util.RenderUtil;
import myau.util.font.FontManager;

import java.util.Arrays;
import java.util.List;

public class ModeElement extends SettingElement {
    private final ModeProperty prop;
    private boolean expanded;
    private float anim;
    private static final int ITEM_H = 16;

    public ModeElement(ModeProperty prop, int x, int y, int width) {
        super(x, y, width, Theme.SETTING_H);
        this.prop = prop;
    }

    @Override
    public boolean isVisible() {
        return prop.isVisible();
    }

    @Override
    public int getHeight() {
        List<String> modes = getModes();
        return Theme.SETTING_H + (int) (anim * modes.size() * ITEM_H);
    }

    private List<String> getModes() {
        return Arrays.asList(prop.getValuePrompt().split(", "));
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float alpha) {
        if (!isVisible()) return;
        int a = (int) (255 * alpha);
        List<String> modes = getModes();
        anim = AnimationUtil.animateSmooth(expanded ? 1f : 0f, anim, 12f, Element.deltaTime);

        // 標題列
        RenderUtil.drawRoundedRect(x, y, width, Theme.SETTING_H, Theme.RADIUS_SM,
                Theme.rgba(Theme.MODULE, a), true, true, true, true);

        String text = prop.getName() + ": " + prop.getModeString();
        int tc = Theme.rgba(Theme.TEXT, a);
        float ty = y + (Theme.SETTING_H - 8) / 2f;
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(text, x + 6, ty, tc);
            float aw = (float) FontManager.productSans16.getStringWidth(expanded ? "v" : "^");
            FontManager.productSans16.drawString(expanded ? "v" : "^", x + width - aw - 6, ty, Theme.rgba(Theme.TEXT_DIM, a));
        }

        // 下拉（不自己 scissor，交給 ClickGuiScreen 內容區裁切）
        if (anim > 0.05f) {
            float dy = y + Theme.SETTING_H;
            float h = modes.size() * ITEM_H * anim;
            RenderUtil.drawRoundedRect(x, dy, width, h, Theme.RADIUS_SM,
                    Theme.rgba(Theme.SETTING_BG, a), true, true, true, true);

            for (int i = 0; i < modes.size(); i++) {
                int iy = (int) (dy + i * ITEM_H);
                boolean hov = mouseX >= x && mouseX <= x + width && mouseY >= iy && mouseY < iy + ITEM_H;
                int c = hov ? Theme.rgba(Theme.ACCENT, a) : Theme.rgba(Theme.TEXT_DIM, a);
                if (FontManager.productSans16 != null) {
                    FontManager.productSans16.drawString(modes.get(i), x + 8, iy + 4, c);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + Theme.SETTING_H) {
            if (button == 0 || button == 1) {
                expanded = !expanded;
                return true;
            }
        }
        if (expanded && anim > 0.5f) {
            List<String> modes = getModes();
            float dy = y + Theme.SETTING_H;
            for (int i = 0; i < modes.size(); i++) {
                int iy = (int) (dy + i * ITEM_H);
                if (mouseX >= x && mouseX <= x + width && mouseY >= iy && mouseY < iy + ITEM_H) {
                    prop.setValue(i);
                    expanded = false;
                    return true;
                }
            }
        }
        return false;
    }
}
