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
import net.minecraft.entity.Entity;

public class FreeLook extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    // === Hold Key 設定（公開 final field，會被自動註冊）===
    public final IntProperty holdKey = new IntProperty("Hold-Key", 0, 0, 223);

    public float freeYaw, freePitch;
    public float prevFreeYaw, prevFreePitch;
    private int previousPerspective = 0;

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

        // Hold Key 邏輯：只有持續按住指定鍵時才啟用 FreeLook
        boolean shouldHold = holdKey.getValue() != 0 && KeyBindUtil.isKeyDown(holdKey.getValue());
        
        if (isEnabled() != shouldHold) {
            setEnabled(shouldHold);
        }
    }

    @EventTarget
    public void onRenderLiving(RenderLivingEvent event) {
        if (isEnabled() && event.getEntity() == mc.thePlayer) {
            // 可在此處理渲染取消
        }
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer == null) return;
        mc.gameSettings.thirdPersonView = previousPerspective;
    }

    public void handleMouseInput(float deltaYaw, float deltaPitch) {
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
