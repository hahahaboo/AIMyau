package myau.ui.impl.clickgui.element.setting;

import myau.property.Property;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import myau.ui.impl.clickgui.Theme;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import org.lwjgl.input.Mouse;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SliderElement extends SettingElement {
    private final Property<?> prop;
    private final double min, max, step;
    private boolean dragging;

    public SliderElement(Property<?> prop, int x, int y, int width) {
        super(x, y, width, Theme.SETTING_H + 6);
        this.prop = prop;
        if (prop instanceof IntProperty) {
            min = ((IntProperty) prop).getMinimum();
            max = ((IntProperty) prop).getMaximum();
            step = 1;
        } else if (prop instanceof PercentProperty) {
            min = 0; max = 100; step = 1;
        } else {
            min = ((FloatProperty) prop).getMinimum();
            max = ((FloatProperty) prop).getMaximum();
            step = 0.05;
        }
    }

    @Override
    public boolean isVisible() {
        return prop.isVisible();
    }

    private double getValue() {
        if (prop instanceof IntProperty || prop instanceof PercentProperty) return (Integer) prop.getValue();
        return (Float) prop.getValue();
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float alpha) {
        if (!isVisible()) return;
        if (dragging) {
            if (Mouse.isButtonDown(0)) update(mouseX);
            else dragging = false;
        }

        int a = (int) (255 * alpha);
        double val = getValue();
        double progress = (max - min == 0) ? 0 : (val - min) / (max - min);
        progress = Math.max(0, Math.min(1, progress));

        // 名稱 + 數值
        String name = prop.getName();
        String valStr = (prop instanceof PercentProperty) ? ((int) val) + "%" :
                (prop instanceof IntProperty) ? String.valueOf((int) val) :
                        String.format("%.2f", val);

        int tc = Theme.rgba(Theme.TEXT, a);
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(name, x + 2, y + 2, tc);
            float vw = (float) FontManager.productSans16.getStringWidth(valStr);
            FontManager.productSans16.drawString(valStr, x + width - vw - 2, y + 2, tc);
        } else {
            mc.fontRendererObj.drawStringWithShadow(name, x + 2, y + 2, tc);
            mc.fontRendererObj.drawStringWithShadow(valStr, x + width - mc.fontRendererObj.getStringWidth(valStr) - 2, y + 2, tc);
        }

        // 軌道
        int trackY = y + height - 9;
        int trackH = 3;
        RenderUtil.drawRoundedRect(x + 2, trackY, width - 4, trackH, 1.5f, Theme.rgba(Theme.SLIDER_BG, a), true, true, true, true);
        float fill = (float) ((width - 4) * progress);
        RenderUtil.drawRoundedRect(x + 2, trackY, fill, trackH, 1.5f, Theme.rgba(Theme.ACCENT, a), true, true, true, true);
        // 圓點
        RenderUtil.drawRoundedRect(x + 2 + fill - 3, trackY - 2, 7, trackH + 4, 3.5f, -1, true, true, true, true);
    }

    private void update(int mouseX) {
        double p = (mouseX - (x + 2.0)) / (width - 4.0);
        p = Math.max(0, Math.min(1, p));
        double nv = min + (max - min) * p;
        if (prop instanceof IntProperty || prop instanceof PercentProperty) {
            prop.setValue((int) Math.round(nv));
        } else {
            double stepped = Math.round(nv / step) * step;
            BigDecimal bd = new BigDecimal(stepped).setScale(2, RoundingMode.HALF_UP);
            prop.setValue((float) Math.max(min, Math.min(max, bd.doubleValue())));
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            dragging = true;
            update(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        dragging = false;
    }
}
