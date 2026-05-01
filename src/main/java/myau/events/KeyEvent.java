package myau.events;

import myau.event.events.Event;

public class KeyEvent implements Event {

    private final int keyCode;
    private final boolean pressed;

    /**
     * 原有建構子 - 保持完全相容（大多數模組使用這個）
     */
    public KeyEvent(int key) {
        this(key, true);   // 預設當作按下事件
    }

    /**
     * 新增建構子 - 給需要 Hold 模式的模組使用
     */
    public KeyEvent(int keyCode, boolean pressed) {
        this.keyCode = keyCode;
        this.pressed = pressed;
    }

    public int getKey() {
        return this.keyCode;
    }

    /**
     * 新增：是否為按下事件
     */
    public boolean isPressed() {
        return this.pressed;
    }

    /**
     * 新增：是否為放開事件
     */
    public boolean isReleased() {
        return !this.pressed;
    }
}
