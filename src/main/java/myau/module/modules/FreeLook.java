package myau.module.modules;

import myau.event.EventTarget;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;

public class FreeLook extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    /** Hold Key（預設可設為 Left Alt 或其他鍵） */
    public final IntProperty holdKey = new IntProperty("Hold-Key", 0, 0, 223);

    public float freeYaw, freePitch;
    public float prevFreeYaw, prevFreePitch;
    private int previousPerspective = 0;

    /** 記錄上一次的 enabled 狀態（用於 Hold 放開後恢復） */
    private boolean wasToggled = false;

    public FreeLook() {
        super("FreeLook", "Look around in 3rd person without changing your direction", Category.RENDER, 0, false, false);
    }

    /**
     * 判斷 FreeLook 是否真正生效（Hold Key 按住 或 模組被正常 toggle 開啟）
     */
    public boolean isFreeLookActive() {
        if (holdKey.getValue() != 0 && KeyBindUtil.isKeyDown(holdKey.getValue())) {
            return true;
        }
        return isEnabled();
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer == null) return;
        wasToggled = true;

        previousPerspective = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 1;

        freeYaw = prevFreeYaw = mc.thePlayer.rotationYaw;
        freePitch = prevFreePitch = mc.thePlayer.rotationPitch;
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer == null) return;
        wasToggled = false;
        mc.gameSettings.thirdPersonView = previousPerspective;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.thePlayer == null || event.getType() != EventType.PRE) return;

        prevFreeYaw = freeYaw;
        prevFreePitch = freePitch;

        boolean shouldBeActive = isFreeLookActive();

        // 如果目前狀態與期望不符，切換
        if (shouldBeActive && !isEnabled()) {
            setEnabled(true);           // 觸發 onEnabled
        } else if (!shouldBeActive && isEnabled() && !wasToggled) {
            setEnabled(false);          // Hold 放開時才自動關閉
        }
    }

    public void handleMouseInput(float deltaYaw, float deltaPitch) {
        if (!isFreeLookActive()) return;

        this.freeYaw += deltaYaw * 0.15F;
        this.freePitch -= deltaPitch * 0.15F;

        if (this.freePitch > 90.0F) this.freePitch = 90.0F;
        if (this.freePitch < -90.0F) this.freePitch = -90.0F;
    }

    @Override
    public String[] getSuffix() {
        int key = holdKey.getValue();
        return new String[]{key == 0 ? "None" : KeyBindUtil.getKeyName(key)};
    }
}
