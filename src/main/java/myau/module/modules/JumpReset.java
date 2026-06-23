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
import java.util.Random;

public class JumpReset extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final BooleanProperty dbg = new BooleanProperty("debug", false);

    private boolean jumpFlag = false;
    private final Random randomChance = new Random();

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
                
                boolean applyThisTime = this.randomChance.nextDouble() <= (double) this.chance.getValue() / 100.0;
                if (applyThisTime) {
                    mc.thePlayer.movementInput.jump = true;
                    if (dbg.getValue()) {
                        ChatUtil.sendFormatted(Myau.clientName + "Jump");
                    }
                }
            }
        }
    }

    @Override
    public String[] getSuffix() {
        int chanceValue = this.chance.getValue();
        return new String[]{chanceValue + "%"};
    }
}
