package myau.module.modules;

import myau.module.Category;
import myau.module.Module;
import net.minecraft.client.Minecraft;

public class FreeLook extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

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
        mc.gameSettings.thirdPersonView = 1;  // 強制切到第三人稱

        freeYaw = prevFreeYaw = mc.thePlayer.rotationYaw;
        freePitch = prevFreePitch = mc.thePlayer.rotationPitch;
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer == null) return;
        mc.gameSettings.thirdPersonView = previousPerspective;
    }

    /**
     * 由 Mixin 呼叫，處理滑鼠移動 delta
     */
    public void handleMouseInput(float deltaYaw, float deltaPitch) {
        this.freeYaw += deltaYaw * 0.15F;
        this.freePitch -= deltaPitch * 0.15F;

        this.freePitch = Math.max(-90.0F, Math.min(90.0F, this.freePitch));
    }

    /**
     * 每 Tick 更新 prev 值（用於平滑渲染）
     */
    public void onTick() {
        if (mc.thePlayer == null || !isEnabled()) return;
        prevFreeYaw = freeYaw;
        prevFreePitch = freePitch;
    }
}
