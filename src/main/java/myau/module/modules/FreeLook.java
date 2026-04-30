package myau.module.modules;

import myau.module.Category;
import myau.module.Module;
import myau.event.EventTarget;
import myau.events.RenderLivingEvent;
import myau.events.TickEvent;
import myau.event.types.EventType;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.List;

public class FreeLook extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();

    public float freeYaw, freePitch;
    public float prevFreeYaw, prevFreePitch;
    private int previousPerspective = 0;

    public FreeLook() {
        super("FreeLook", "Look around in 3rd person without changing your direction", Category.RENDER, 0, false, false);
    }

    public List<String> getSettings() {
        return Arrays.asList("No configurable settings.");
    }

    public boolean isHoldModule() {
        return true;
    }

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
        // 暫時不做取消，交由 mixin 處理玩家模型
    }

    public void onDisabled() {
        if (mc.thePlayer == null) return;
        mc.gameSettings.thirdPersonView = previousPerspective;
    }

    /**
     * 供 Mixin 呼叫處理滑鼠移動
     */
    public void handleMouseInput(float deltaYaw, float deltaPitch) {
        this.freeYaw += deltaYaw * 0.15F;
        this.freePitch -= deltaPitch * 0.15F;

        this.freePitch = Math.max(-90.0F, Math.min(90.0F, this.freePitch));
    }
}
