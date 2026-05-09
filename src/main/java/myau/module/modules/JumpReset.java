package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.KnockbackEvent;
import myau.events.LivingUpdateEvent;
import myau.mixin.IAccessorEntity;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;

public class JumpReset extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final BooleanProperty dbg = new BooleanProperty("debug", false);

    private boolean jumpFlag = false;
    private int chanceCounter = 0;   // 新增，參考 Velocity 的 counter 機制

    public JumpReset() {
        super("JumpReset", "Jump reset on knockback", Category.COMBAT, 0, false, false);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            this.jumpFlag = event.getY() > 0.0;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.jumpFlag) {
            this.jumpFlag = false;
            if (mc.thePlayer.onGround && mc.thePlayer.isSprinting() && !mc.thePlayer.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb()) {
                
                this.chanceCounter = this.chanceCounter % 100 + this.chance.getValue();
                if (this.chanceCounter >= 100) {
                    mc.thePlayer.movementInput.jump = true;
                    if (dbg.getValue()) {
                        ChatUtil.sendFormatted(Myau.clientName + "Jump");
                    }
                }
            }
        }
    }
}
