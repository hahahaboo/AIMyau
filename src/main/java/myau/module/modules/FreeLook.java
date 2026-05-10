package myau.module.modules;

import myau.event.EventTarget;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;

public class FreeLook extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    // Hold-Key（按住啟用）
    public final IntProperty holdKey = new IntProperty("Hold-Key", 0, 0, 223);

    public float freeYaw, freePitch;
    public float prevFreeYaw, prevFreePitch;
    private int previousPerspective = 0;

    private boolean toggled = false;

    public FreeLook() {
        super("FreeLook", "Look around in 3rd person without changing your direction", Category.RENDER, 0, false, false);
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer == null) return;

        previousPerspective = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 1;

        freeYaw = prevFreeYaw = mc.thePlayer.rotationYaw;
        freePitch = prevFreePitch = mc.thePlayer.rotationPitch;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.getKey() == getKey() && getKey() != 0) {
            toggled = !toggled;
            setEnabled(toggled || isHoldActive());
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.thePlayer == null || event.getType() != EventType.PRE) return;

        prevFreeYaw = freeYaw;
        prevFreePitch = freePitch;

        boolean holdActive = isHoldActive();
        boolean shouldBeEnabled = toggled || holdActive;

        if (isEnabled() != shouldBeEnabled) {
            setEnabled(shouldBeEnabled);
        }
    }

    private boolean isHoldActive() {
        return holdKey.getValue() != 0 && KeyBindUtil.isKeyDown(holdKey.getValue());
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer == null) return;
        mc.gameSettings.thirdPersonView = previousPerspective;
        // toggled 不重置，讓玩家可以繼續用 Toggle 控制
    }

    public void handleMouseInput(float deltaYaw, float deltaPitch) {
        this.freeYaw += deltaYaw * 0.15F;
        this.freePitch -= deltaPitch * 0.15F;

        if (this.freePitch > 90.0F) this.freePitch = 90.0F;
        if (this.freePitch < -90.0F) this.freePitch = -90.0F;
    }

    @Override
    public String[] getSuffix() {
        boolean currentlyUsingHold = isHoldActive();
        boolean currentlyUsingToggle = toggled;

        if (!isEnabled()) {
            return new String[]{""};
        }

        // 優先顯示 Toggle（因為是持續狀態）
        if (currentlyUsingToggle && getKey() != 0) {
            return new String[]{"Toggle - " + KeyBindUtil.getKeyName(getKey())};
        }

        // Hold 模式
        if (currentlyUsingHold && holdKey.getValue() != 0) {
            return new String[]{"Hold - " + KeyBindUtil.getKeyName(holdKey.getValue())};
        }

        // 保底顯示（不應該走到這裡）
        return new String[]{"FreeLook"};
    }
}
