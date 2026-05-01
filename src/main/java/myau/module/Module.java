package myau.module;

import lombok.Getter;
import lombok.Setter;
import myau.Myau;
import myau.event.EventTarget;           // ← 新增
import myau.events.KeyEvent;             // ← 新增
import myau.module.modules.HUD;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;

public abstract class Module {
    protected static final Minecraft mc = Minecraft.getMinecraft();

    @Getter
    protected final String name;
    @Getter
    protected final String description;
    @Getter
    protected final Category category;
    protected final boolean defaultEnabled;
    protected final int defaultKey;
    protected final boolean defaultHidden;

    @Getter
    protected boolean enabled;
    @Setter
    @Getter
    protected int key;
    @Setter
    @Getter
    protected boolean hidden;

    public Module(String name, String description, Category category, int key, boolean enabled, boolean hidden) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.key = this.defaultKey = key;
        this.enabled = this.defaultEnabled = enabled;
        this.hidden = this.defaultHidden = hidden;
    }

    public Module(String name, boolean enabled, boolean hidden) {
        this(name, "", Category.MISC, 0, enabled, hidden);
    }

    public Module(String name, boolean enabled) {
        this(name, enabled, false);
    }

    public String formatModule() {
        return String.format(
                "%s%s &r(%s&r)",
                this.key == 0 ? "" : String.format("&l[%s] &r", KeyBindUtil.getKeyName(this.key)),
                this.name,
                this.enabled ? "&a&lON" : "&c&lOFF"
        );
    }

    public String[] getSuffix() {
        return new String[0];
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                this.onEnabled();
            } else {
                this.onDisabled();
            }
        }
    }

    public boolean toggle() {
        boolean enabled = !this.enabled;
        this.setEnabled(enabled);
        if (this.enabled == enabled) {
            if (((HUD) Myau.moduleManager.modules.get(HUD.class)).toggleSound.getValue()) {
                Myau.moduleManager.playSound();
            }
            return true;
        } else {
            return false;
        }
    }

    public void onEnabled() {
    }

    public void onDisabled() {
    }

    public void verifyValue(String string) {
    }

    /**
     * 是否為 Hold 模式模組（預設 false）
     * FreeLook 等少數模組需 override 回傳 true
     */
    public boolean isHoldModule() {
        return false;
    }

    /**
     * 統一處理 KeyEvent
     * - 一般模組：只在按下時 toggle（恢復原本行為）
     * - Hold 模組：按下時開啟，放開時關閉
     */
@EventTarget
    public void onKey(KeyEvent event) {
        if (event.getKey() != this.getKey()) return;

        if (isHoldModule()) {
            // Hold 模式 (FreeLook)
            if (event.isPressed()) {
                if (!isEnabled()) {
                    setEnabled(true);
                }
            } else {
                if (isEnabled()) {
                    setEnabled(false);
                }
            }
        } else {
            // 一般模組：只在按下瞬間 (pressed == true) 才 toggle
            // 避免按下與放開都觸發
            if (event.isPressed(true)) {
                toggle();
            }
        }
    }
}
