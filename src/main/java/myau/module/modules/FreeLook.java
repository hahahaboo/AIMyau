package myau.module.modules;

import myau.event.EventTarget;
import myau.events.RenderLivingEvent;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Category;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

public class FreeLook extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    public float freeYaw, freePitch;
    public float prevFreeYaw, prevFreePitch;
    private int previousPerspective = 0;

    public FreeLook() {
        super("FreeLook", "Look around in 3rd person without changing your direction", Category.RENDER, 0, false, false);
    }

    public boolean isHoldModule() {
        return true;
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
        if (mc.thePlayer == null || event.getType() != EventType.PRE || !isEnabled()) return;
        
        prevFreeYaw = freeYaw;
        prevFreePitch = freePitch;
    }

    @EventTarget
    public void onRenderLiving(RenderLivingEvent event) {
        if (isEnabled() && event.getEntity() == mc.thePlayer) {
            // 取消渲染玩家模型（對應原 Forge RenderLivingEvent.Specials.Pre）
            // 若需要更精確取消可調整 Event 或 Mixin
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
}
