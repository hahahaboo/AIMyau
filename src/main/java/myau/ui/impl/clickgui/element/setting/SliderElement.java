package myau.ui.impl.clickgui.element.setting;

import myau.property.Property;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import myau.ui.impl.clickgui.Theme;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SliderElement extends SettingElement {
    private final Property<?> prop;
    private final double min, max, step;
    private boolean dragging;

    /** 點擊數值後進入編輯 */
    private boolean focused;
    private String inputBuffer = "";

    public SliderElement(Property<?> prop, int x, int y, int width) {
        super(x, y, width, Theme.SETTING_H + 8);
        this.prop = prop;
        if (prop instanceof IntProperty) {
            min = ((IntProperty) prop).getMinimum();
            max = ((IntProperty) prop).getMaximum();
            step = 1;
        } else if (prop instanceof PercentProperty) {
            min = 0;
            max = 100;
            step = 1;
        } else {
            min = ((FloatProperty) prop).getMinimum();
            max = ((FloatProperty) prop).getMaximum();
            step = 0.05;
        }
    }

    public boolean isFocused() {
        return focused;
    }

    /** 套用後失焦（點其他位置、右鍵數值、Enter） */
    public void unfocus() {
        if (!focused) return;
        applyInput();
        focused = false;
        inputBuffer = "";
    }

    /** 取消修改並失焦（點軌道、Esc、關閉 GUI） */
    public void cancelFocus() {
        focused = false;
        inputBuffer = "";
    }

    private boolean isFloatProp() {
        return prop instanceof FloatProperty;
    }

    private double getValue() {
        if (prop instanceof IntProperty || prop instanceof PercentProperty) return (Integer) prop.getValue();
        return (Float) prop.getValue();
    }

    private String formatValue(double val) {
        if (prop instanceof PercentProperty) return ((int) val) + "%";
        if (prop instanceof IntProperty) return String.valueOf((int) val);
        return String.format("%.2f", val);
    }

    /** 數值文字區域（右側） */
    private boolean isInsideValueText(int mouseX, int mouseY) {
        int textY = y + height - 26;
        String valStr = focused ? (inputBuffer.isEmpty() ? " " : inputBuffer) : formatValue(getValue());
        float vw;
        if (FontManager.productSans16 != null) {
            vw = (float) FontManager.productSans16.getStringWidth(valStr);
        } else {
            vw = mc.fontRendererObj.getStringWidth(valStr);
        }
        int pad = 4;
        float left = x + width - vw - 2 - pad;
        float right = x + width - 2 + pad;
        return mouseX >= left && mouseX <= right
                && mouseY >= textY - 2 && mouseY <= textY + 12;
    }

    private void applyInput() {
        if (inputBuffer == null || inputBuffer.isEmpty() || inputBuffer.equals(".") || inputBuffer.equals("-")) {
            return;
        }
        try {
            if (isFloatProp()) {
                float v = Float.parseFloat(inputBuffer);
                double stepped = Math.round(v / step) * step;
                BigDecimal bd = new BigDecimal(stepped).setScale(2, RoundingMode.HALF_UP);
                float clamped = (float) Math.max(min, Math.min(max, bd.doubleValue()));
                prop.setValue(clamped);
            } else {
                int v = (int) Math.round(Double.parseDouble(inputBuffer));
                int clamped = (int) Math.max(min, Math.min(max, v));
                prop.setValue(clamped);
            }
        } catch (NumberFormatException ignored) {
            // 非法輸入不套用
        }
    }

    @Override
    public boolean isVisible() {
        return prop.isVisible();
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float alpha) {
        if (!isVisible()) return;
        if (dragging && !focused) {
            if (Mouse.isButtonDown(0)) update(mouseX);
            else dragging = false;
        }

        int a = (int) (255 * alpha);
        double val = getValue();
        double progress = (max - min == 0) ? 0 : (val - min) / (max - min);
        progress = Math.max(0, Math.min(1, progress));

        String name = prop.getName();
        String valStr = focused ? (inputBuffer + "_") : formatValue(val);

        int tc = Theme.rgba(Theme.TEXT, a); // focused 也不改色，只顯示 "_"
        int textY = y + height - 26;
        if (FontManager.productSans16 != null) {
            FontManager.productSans16.drawString(name, x + 2, textY, tc);
            float vw = (float) FontManager.productSans16.getStringWidth(valStr);
            FontManager.productSans16.drawString(valStr, x + width - vw - 2, textY, tc);
        } else {
            mc.fontRendererObj.drawStringWithShadow(name, x + 2, textY, tc);
            mc.fontRendererObj.drawStringWithShadow(valStr, x + width - mc.fontRendererObj.getStringWidth(valStr) - 2, textY, tc);
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
        if (!isVisible()) return false;

        // 右鍵數值 → 套用並失焦
        if (button == 1 && isInsideValueText(mouseX, mouseY)) {
            unfocus();
            return true;
        }

        if (button == 0) {
            // 點數值 → 進入編輯
            if (isInsideValueText(mouseX, mouseY)) {
                focused = true;
                dragging = false;
                if (prop instanceof PercentProperty || prop instanceof IntProperty) {
                    inputBuffer = String.valueOf((int) getValue());
                } else {
                    float fv = (Float) prop.getValue();
                    if (fv == (int) fv) inputBuffer = String.valueOf((int) fv);
                    else inputBuffer = String.valueOf(fv);
                }
                return true;
            }
            // 點軌道（或本列其他區域）→ 取消修改並開始拖曳
            if (isHovered(mouseX, mouseY)) {
                if (focused) {
                    cancelFocus();
                }
                dragging = true;
                update(mouseX);
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        dragging = false;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!focused) return;

        if (keyCode == Keyboard.KEY_BACK && !inputBuffer.isEmpty()) {
            inputBuffer = inputBuffer.substring(0, inputBuffer.length() - 1);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            unfocus();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelFocus();
            return;
        }

        // 僅允許數字；Float 額外允許一個小數點
        if (typedChar >= '0' && typedChar <= '9') {
            inputBuffer += typedChar;
            return;
        }
        if (isFloatProp() && typedChar == '.' && !inputBuffer.contains(".")) {
            inputBuffer += ".";
        }
    }
}
