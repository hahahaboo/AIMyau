package myau.ui.impl.clickgui.element.setting;

import myau.ui.impl.clickgui.element.Element;

public abstract class SettingElement extends Element {
    public SettingElement(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public abstract boolean isVisible();
}
