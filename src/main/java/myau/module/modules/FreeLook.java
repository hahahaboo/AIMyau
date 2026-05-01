package myau.module.modules;

import myau.event.EventTarget;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.event.types.EventType;   // 保留 import 以使用 EventType.POST
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

    public void handleMouseInput(float deltaYaw, float deltaPitch) {
        this.freeYaw += deltaYaw * 0.15F;
        this.freePitch -= deltaPitch * 0.15F;

        this.freePitch = Math.max(-90.0F, Math.min(90.0F, this.freePitch));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        // 使用 EventType.POST，這是 AIMyau 中常見的 Client Tick 處理時機
        if (event.getType() != EventType.POST || !isEnabled() || mc.thePlayer == null) 
            return;
        
        prevFreeYaw = freeYaw;
        prevFreePitch = freePitch;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.getKey() != this.getKey()) return;

        if (isHoldModule()) {
            // Hold 模式：按下開啟，放開關閉
            if (event.isPressed()) {
                if (!isEnabled()) {
                    setEnabled(true);
                }
            } else {
                if (isEnabled()) {
                    setEnabled(false);
                }
            }
        } 
        else {
            // 一般模組：只在按下時 toggle（避免放開時又觸發）
            if (event.isPressed()) {
                toggle();
            }
        }
    }
}
