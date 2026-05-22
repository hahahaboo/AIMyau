package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.lwjgl.input.Mouse;

public class KnockbackDelay extends Module {

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Delay", "Blink"});
    public final FloatProperty distance = new FloatProperty("distance", 6.0f, 3.0f, 12.0f);
    public final FloatProperty chance = new FloatProperty("chance", 100f, 0f, 100f);
    public final FloatProperty maxDelay = new FloatProperty("max-delay", 200f, 50f, 1000f);

    public final BooleanProperty inAir = new BooleanProperty("in-air", true);
    public final BooleanProperty lookingAtPlayer = new BooleanProperty("looking-at-player", false);
    public final BooleanProperty requireLMB = new BooleanProperty("require-lmb", false);

    public KnockbackDelay() {
        super("Knockback Delay", "Delays knockback packets using blink", Category.COMBAT, 0, false, false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE || !isEnabled()) return;

        // 收到 TP 封包強制釋放
        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            resetBlink();
            return;
        }

        if (!(event.getPacket() instanceof S12PacketEntityVelocity)) return;

        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;

        if (!shouldDelay()) return;

        // 機率判斷
        if (chance.getValue() < 100 && Math.random() * 100 >= chance.getValue()) return;

        if (mode.getValue() == 1) { // Blink 模式
            if (Myau.blinkManager.getBlinkingModule() != BlinkModules.KNOCKBACK_DELAY) {
                Myau.blinkManager.setBlinkState(true, BlinkModules.KNOCKBACK_DELAY);
            }
            event.setCancelled(true);
        } else {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.POST || !isEnabled()) return;

        if (Myau.blinkManager.getBlinkingModule() == BlinkModules.KNOCKBACK_DELAY) {
            if (!shouldDelay()) {
                resetBlink();
            }
        }
    }

    private boolean shouldDelay() {
        if (!isTargetInRange()) return false;
        if (inAir.getValue() && mc.thePlayer.onGround) return false;
        if (lookingAtPlayer.getValue() && !isLookingAtPlayer()) return false;
        if (requireLMB.getValue() && !Mouse.isButtonDown(0)) return false;

        return true;
    }

    private boolean isTargetInRange() {
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || player.getHealth() <= 0) continue;
            if (Myau.friendManager.isFriend(player.getName())) continue;

            double dist = mc.thePlayer.getDistanceToEntity(player);
            if (dist <= distance.getValue()) {
                return true;
            }
        }
        return false;
    }

    private boolean isLookingAtPlayer() {
        return mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof EntityPlayer;
    }

    private void resetBlink() {
        if (Myau.blinkManager.getBlinkingModule() == BlinkModules.KNOCKBACK_DELAY) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.KNOCKBACK_DELAY);
        }
    }

    @Override
    public void onDisabled() {
        resetBlink();
        super.onDisabled();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.valueOf((int) maxDelay.getValue()) + "ms"};
    }
}
