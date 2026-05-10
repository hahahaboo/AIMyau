package myau.module.modules;

import myau.event.EventTarget;
import myau.events.RenderLivingEvent;
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

    private boolean toggled = false;   // 使用 Module 本身 Keybind 的 Toggle 狀態

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
    public void onTick(TickEvent event) {
        if (mc.thePlayer == null || event.getType() != EventType.PRE) return;

        prevFreeYaw = freeYaw;
        prevFreePitch = freePitch;

        // === Toggle 模式：使用玩家設定的 FreeLook Keybind ===
        if (getKey() != 0 && KeyBindUtil.isKeyPressed(getKey())) {
            toggled = !toggled;
        }

        // === Hold 模式 ===
        boolean holdActive = holdKey.getValue() != 0 && KeyBindUtil.isKeyDown(holdKey.getValue());

        boolean shouldBeEnabled = toggled || holdActive;

        // 只有狀態真正改變時才切換
        if (isEnabled() != shouldBeEnabled) {
            setEnabled(shouldBeEnabled);
        }
    }

    @EventTarget
    public void onRenderLiving(RenderLivingEvent event) {
        // 可在此處理渲染相關
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer == null) return;
        mc.gameSettings.thirdPersonView = previousPerspective;
        toggled = false;  // 關閉模組時重置 Toggle 狀態
    }

    public void handleMouseInput(float deltaYaw, float deltaPitch) {
        this.freeYaw += deltaYaw * 0.15F;
        this.freePitch -= deltaPitch * 0.15F;

        if (this.freePitch > 90.0F) this.freePitch = 90.0F;
        if (this.freePitch < -90.0F) this.freePitch = -90.0F;
    }

    @Override
    public String[] getSuffix() {
        String holdStr = holdKey.getValue() == 0 ? "" : "H:" + KeyBindUtil.getKeyName(holdKey.getValue());
        String toggleStr = getKey() == 0 ? "" : "T:" + KeyBindUtil.getKeyName(getKey());

        if (!holdStr.isEmpty() && !toggleStr.isEmpty()) {
            return new String[]{holdStr + " | " + toggleStr};
        } else if (!holdStr.isEmpty()) {
            return new String[]{holdStr};
        } else if (!toggleStr.isEmpty()) {
            return new String[]{toggleStr};
        }
        return new String[]{"None"};
    }
}
