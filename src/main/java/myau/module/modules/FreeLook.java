package myau.module.modules;

import myau.event.EventTarget;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Category;
import myau.module.Module;
import net.minecraft.client.Minecraft;

public class FreeLook extends Module {

    protected static final Minecraft mc = Minecraft.getMinecraft();

    public float freeYaw, freePitch;
    public float prevFreeYaw, prevFreePitch;
    private int previousPerspective = 0;

    public FreeLook() {
        super("FreeLook", "Look around freely in third person without changing movement direction", 
              Category.RENDER, 0, false, false);
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

    @Override
    public void onDisabled() {
        if (mc.thePlayer == null) return;
        mc.gameSettings.thirdPersonView = previousPerspective;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE || !isEnabled() || mc.thePlayer == null) 
            return;
        
        prevFreeYaw = freeYaw;
        prevFreePitch = freePitch;
    }

    public void handleMouseInput(float deltaYaw, float deltaPitch) {
        this.freeYaw += deltaYaw * 0.15F;   // 增加靈敏度
        this.freePitch -= deltaPitch * 0.15F;

        if (this.freePitch > 90.0F) this.freePitch = 90.0F;
        if (this.freePitch < -90.0F) this.freePitch = -90.0F;
    }
}
